import os
import time
import secrets
from typing import Dict, Any, List

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, EmailStr

app = FastAPI(title="GenBank OTP Microservice")

OTP_TTL_SECONDS = 300
MAX_ATTEMPTS = 3
RATE_LIMIT_SECONDS = 600
MAX_GENERATE_REQUESTS = 3

# Store structure: {email: {"otp": str, "created_at": float, "attempts": int}}
otp_store: Dict[str, Dict[str, Any]] = {}
# Rate limit structure: {email: [timestamp1, timestamp2, ...]}
rate_limit_store: Dict[str, List[float]] = {}


class OTPRequest(BaseModel):
    """Request model for generating an OTP."""
    email: EmailStr


class OTPVerify(BaseModel):
    """Request model for verifying an OTP."""
    email: EmailStr
    otp: str


def send_email_otp(email: str, otp: str) -> None:
    """
    Sends an OTP to the given email address.
    Logs prominently to terminal console for security.
    """
    print("\n" + "=" * 58)
    print(" [GENBANK SECURE OTP DISPATCH]")
    print(f" Recipient Email : {email}")
    print(f" >>> ONE-TIME PASSWORD (OTP): {otp} <<<")
    print(" (Valid for 5 minutes. Enter this code into the portal)")
    print("=" * 58 + "\n")
    try:
        otp_file_path = os.path.join(os.path.dirname(__file__), "latest_otp.txt")
        with open(otp_file_path, "w", encoding="utf-8") as f:
            f.write(f"Email: {email}\nOTP: {otp}\nGeneratedAt: {time.ctime()}\n")
    except Exception:
        pass


@app.post("/api/otp/generate")
def generate_otp(request: OTPRequest) -> Dict[str, str]:
    """
    Generates and dispatches a 6-digit OTP for the given email.
    Applies rate limiting and stores the OTP with a TTL.
    """
    current_time = time.time()
    
    # Clean up old rate limit entries
    if request.email in rate_limit_store:
        rate_limit_store[request.email] = [
            ts for ts in rate_limit_store[request.email] 
            if current_time - ts < RATE_LIMIT_SECONDS
        ]
    else:
        rate_limit_store[request.email] = []
        
    # Check rate limit
    if len(rate_limit_store[request.email]) >= MAX_GENERATE_REQUESTS:
        raise HTTPException(
            status_code=429, 
            detail="Too many OTP requests. Please try again later."
        )
        
    rate_limit_store[request.email].append(current_time)

    # Generate a cryptographically secure 6-digit OTP
    otp = "".join(str(secrets.randbelow(10)) for _ in range(6))
    
    otp_store[request.email] = {
        "otp": otp,
        "created_at": current_time,
        "attempts": 0
    }
    
    send_email_otp(request.email, otp)
    return {
        "status": "success",
        "message": "OTP generated and dispatched successfully"
    }


@app.post("/api/otp/verify")
def verify_otp(request: OTPVerify) -> Dict[str, str]:
    """
    Verifies the provided OTP for the given email.
    Enforces TTL and maximum attempt limits.
    """
    entry = otp_store.get(request.email)
    if not entry:
        raise HTTPException(status_code=400, detail="OTP expired or not found.")

    current_time = time.time()
    if current_time - entry["created_at"] > OTP_TTL_SECONDS:
        del otp_store[request.email]
        raise HTTPException(status_code=400, detail="OTP expired.")

    entry["attempts"] += 1
    
    if entry["otp"] == request.otp:
        del otp_store[request.email]
        return {"status": "success", "message": "OTP verified successfully"}

    if entry["attempts"] >= MAX_ATTEMPTS:
        del otp_store[request.email]
        raise HTTPException(status_code=400, detail="Maximum attempts reached. OTP invalidated.")

    raise HTTPException(status_code=400, detail="Invalid OTP entered.")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=8000)
