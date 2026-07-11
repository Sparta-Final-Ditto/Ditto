#!/usr/bin/env python3
"""matching/follow/feed 로컬 동작 확인용 시딩 + 검증 스크립트.

scripts/seed-data/testers.json 에 정의된 테스터 계정들을 실제 API(회원가입 -> 로그인 ->
위치/관심사 등록 -> 게시물 작성 -> 팔로우)로 시딩한 뒤, 매칭/피드/팔로우 기능이 실제로
동작하는지 호출해서 결과를 요약해서 보여준다.

내부 리포지토리를 직접 건드리지 않고 게이트웨이(api-gateway)를 통해 실제 API 플로우를
그대로 타기 때문에, Kafka 이벤트(관심사 등록 -> 임베딩 생성 -> 매칭 서비스 동기화) 등
서비스 간 연동까지 함께 검증된다.

사용법:
    python scripts/seed_and_verify.py
    DITTO_BASE_URL=http://localhost:8080 python scripts/seed_and_verify.py
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

# Windows 콘솔의 기본 코드페이지(cp949 등)로 한글이 깨지는 것을 방지
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

BASE_URL = os.environ.get("DITTO_BASE_URL", "http://localhost:8080")
SEED_FILE = Path(__file__).parent / "seed-data" / "testers.json"


def api(method, path, token=None, body=None):
    url = BASE_URL + path
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            parsed = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            parsed = {"message": raw.decode("utf-8", "replace")}
        return e.code, parsed
    except urllib.error.URLError as e:
        return None, {"message": str(e.reason)}


def msg(data):
    if not data:
        return ""
    return data.get("message") or data.get("data") or data


def seed(testers, report):
    users = {}  # nickname -> {"id": ..., "token": ...}

    for t in testers:
        nick = t["nickname"]
        row = report.setdefault(nick, {})

        status, data = api("POST", "/api/v1/auth/signup", body={
            "email": t["email"], "password": t["password"], "nickname": nick,
            "gender": t["gender"], "birthdate": t["birthdate"],
            "latitude": t["location"]["latitude"], "longitude": t["location"]["longitude"],
        })
        row["signup"] = "created" if status == 201 else f"skipped ({status}: {msg(data)})"

        status, data = api("POST", "/api/v1/auth/login", body={"email": t["email"], "password": t["password"]})
        if status != 200:
            row["login"] = f"FAIL ({status}: {msg(data)})"
            continue
        token = data["data"]["accessToken"]
        row["login"] = "ok"

        status, data = api("GET", "/api/v1/users/me", token=token)
        if status != 200:
            row["me"] = f"FAIL ({status}: {msg(data)})"
            continue
        user_id = data["data"]["id"]
        users[nick] = {"id": user_id, "token": token}

        status, data = api("PATCH", "/api/v1/users/me/location", token=token, body={
            "latitude": t["location"]["latitude"], "longitude": t["location"]["longitude"],
        })
        row["location"] = "ok" if status == 200 else f"FAIL ({status}: {msg(data)})"

        status, data = api("POST", "/api/v1/users/me/interests", token=token, body={"hashtags": t["interests"]})
        row["interests"] = "ok" if status == 200 else f"FAIL ({status}: {msg(data)})"

        post_results = []
        for post in t["posts"]:
            status, data = api("POST", "/api/v1/posts", token=token, body={
                "content": post["content"], "tags": post["tags"],
                "latitude": t["location"]["latitude"], "longitude": t["location"]["longitude"],
                "visibility": "PUBLIC", "showLocation": True,
            })
            post_results.append("ok" if status == 201 else f"FAIL ({status}: {msg(data)})")
        row["posts"] = ", ".join(post_results)

    # 팔로우는 모든 유저가 만들어진 뒤에 처리 (팔로우 대상 userId가 필요)
    for t in testers:
        nick = t["nickname"]
        row = report.setdefault(nick, {})
        follow_results = []
        for target_nick in t.get("follows", []):
            if nick not in users or target_nick not in users:
                follow_results.append(f"{target_nick}: SKIP (user missing)")
                continue
            status, data = api(
                "POST", f"/api/v1/users/{users[target_nick]['id']}/follow",
                token=users[nick]["token"],
            )
            follow_results.append(f"{target_nick}: {'ok' if status == 200 else f'FAIL ({status}: {msg(data)})'}")
        row["follows"] = "; ".join(follow_results) if follow_results else "-"

    return users


def verify(testers, users, report):
    for t in testers:
        nick = t["nickname"]
        row = report.setdefault(nick, {})
        if nick not in users:
            row["verify"] = "SKIP (no account)"
            continue
        token = users[nick]["token"]

        # 팔로워/팔로잉 목록
        status, data = api("GET", f"/api/v1/users/{users[nick]['id']}/followers", token=token)
        followers = len(data["data"]) if status == 200 else None
        status, data = api("GET", f"/api/v1/users/{users[nick]['id']}/followings", token=token)
        followings = len(data["data"]) if status == 200 else None
        row["follow_counts"] = f"followers={followers}, followings={followings}"

        # 피드 3종
        for feed_type in ("follow", "match", "random"):
            status, data = api("GET", f"/api/v1/feeds/{feed_type}?size=20", token=token)
            count = len(data["data"]["feeds"]) if status == 200 and data.get("data") else None
            row[f"feed_{feed_type}"] = f"{count} posts" if status == 200 else f"FAIL ({status}: {msg(data)})"

        # 매칭: 임베딩 동기화가 비동기(Kafka)라 바로 안 되면 몇 번 재시도
        match_body = {"genderFilter": "NONE", "locationFilterOn": False, "minAge": None, "maxAge": None}
        last = None
        for attempt in range(4):
            status, data = api("POST", "/api/v1/matching/today", token=token, body=match_body)
            last = (status, data)
            if status == 200:
                break
            time.sleep(5)
        status, data = last
        if status == 200:
            row["matching"] = f"ok -> matchedUserId={data.get('matchedUserId')} score={data.get('finalScore')}"
        else:
            row["matching"] = f"FAIL after retries ({status}: {msg(data)})"


def print_report(report, order):
    print("\n" + "=" * 78)
    print("SEED + VERIFY REPORT")
    print("=" * 78)
    for nick in order:
        row = report.get(nick, {})
        print(f"\n[{nick}]")
        for key in ("signup", "login", "me", "location", "interests", "posts", "follows",
                    "follow_counts", "feed_follow", "feed_match", "feed_random", "matching"):
            if key in row:
                print(f"  {key:14s}: {row[key]}")
    print("\n" + "=" * 78)


def main():
    with open(SEED_FILE, encoding="utf-8") as f:
        testers = json.load(f)["testers"]

    report = {}
    print(f"Seeding {len(testers)} testers against {BASE_URL} ...")
    users = seed(testers, report)

    print("Seeding done. Waiting a moment for interest-registration events to propagate...")
    time.sleep(5)

    print("Verifying matching / follow / feed ...")
    verify(testers, users, report)

    print_report(report, [t["nickname"] for t in testers])


if __name__ == "__main__":
    main()
