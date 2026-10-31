from app.config import Settings


def test_cors_origins_are_split_and_trimmed() -> None:
    settings = Settings(cors_origins="https://grindpvp.example, http://localhost:5173,")

    assert settings.cors_origin_list == [
        "https://grindpvp.example",
        "http://localhost:5173",
    ]
