from abc import ABC, abstractmethod


class EmbeddingModelPort(ABC):
    @abstractmethod
    def encode(self, text: str) -> list[float]: ...
