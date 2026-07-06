from abc import ABC, abstractmethod


class BatchRunnerPort(ABC):
    @abstractmethod
    async def run_daily(self) -> None: ...

    @abstractmethod
    async def run_monthly(self) -> None: ...
