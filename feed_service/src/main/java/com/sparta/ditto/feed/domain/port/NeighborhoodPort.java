package com.sparta.ditto.feed.domain.port;

public interface NeighborhoodPort {
    String resolveNeighborhood(double latitude, double longitude);
}