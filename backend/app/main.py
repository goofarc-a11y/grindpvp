from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.exc import SQLAlchemyError

from app.config import Settings, get_settings
from app.database import check_database_connection, engine, initialize_database
from app.routes import admin_router, public_router


@asynccontextmanager
async def lifespan(_: FastAPI):
    initialize_database()
    yield


def create_app(settings: Settings | None = None) -> FastAPI:
    app = FastAPI(
        title="GrindPvP API",
        version="0.2.0",
        description="Ranked PvP profiles, leaderboards, match history, and admin tools.",
        lifespan=lifespan,
    )
    app.add_middleware(
        CORSMiddleware,
        allow_origins=(settings or get_settings()).cors_origin_list,
        allow_credentials=False,
        allow_methods=["GET", "POST", "OPTIONS"],
        allow_headers=["Accept", "Authorization", "Content-Type"],
    )
    app.include_router(public_router)
    app.include_router(admin_router)

    @app.get("/health", tags=["health"])
    def health_check() -> dict[str, str]:
        try:
            check_database_connection()
        except SQLAlchemyError as error:
            raise HTTPException(
                status_code=503,
                detail="Database connection is unavailable.",
            ) from error

        return {
            "status": "ok",
            "database": "connected",
            "database_engine": engine.dialect.name,
        }

    return app


app = create_app()
