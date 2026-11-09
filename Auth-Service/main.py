import os
import time
import secrets
import smtplib
from email.message import EmailMessage
from typing import Dict, Any, List

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, EmailStr

app = FastAPI(title="GenBank OTP Microservice")

OTP_TTL_SECONDS = 300
MAX_ATTEMPTS = 3
RATE_LIMIT_SECONDS = 600
MAX_GENERATE_REQUESTS = 3

# Load .env file automatically if present
def _load_env_file():
    candidates = [
        os.path.join(os.path.dirname(__file__), "..", ".env"),
        os.path.join(os.path.dirname(__file__), ".env"),
        ".env"
    ]
    for path in candidates:
        if os.path.isfile(path):
            with open(path, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith("#") and "=" in line:
                        k, v = line.split("=", 1)
                        k = k.strip()
                        v = v.strip().strip('"').strip("'")
                        if k and not os.getenv(k):
                            os.environ[k] = v
            break

_load_env_file()

# SMTP Configuration (Optional - falls back gracefully to demo mode if not set)
SMTP_HOST = os.getenv("SMTP_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.getenv("SMTP_PORT", "587"))
SMTP_USER = os.getenv("SMTP_USER", "")
SMTP_PASSWORD = os.getenv("SMTP_PASSWORD", "")
SMTP_FROM_NAME = os.getenv("SMTP_FROM_NAME", "GenBank Security")

# In-memory stores
otp_store: Dict[str, Dict[str, Any]] = {}
rate_limit_store: Dict[str, List[float]] = {}


class OTPRequest(BaseModel):
    """Request model for generating an OTP."""
    email: EmailStr


class OTPVerify(BaseModel):
    """Request model for verifying an OTP."""
    email: EmailStr
    otp: str


def send_email_otp(email: str, otp: str) -> bool:
    """
    Sends an OTP to the given email address.
    If SMTP credentials are provided, dispatches an authentic branded HTML email.
    Otherwise, logs to terminal and allows graceful demo fallback.
    """
    print("\n" + "=" * 58)
    print(" [GENBANK SECURE OTP DISPATCH]")
    print(f" Recipient Email : {email}")
    print(f" >>> ONE-TIME PASSWORD (OTP): {otp} <<<")
    print(" (Valid for 5 minutes. Enter this code into the portal)")
    print("=" * 58 + "\n")

    dispatched = False

    # Dispatch authentic email if SMTP credentials are provided
    if SMTP_USER and SMTP_PASSWORD:
        try:
            msg = EmailMessage()
            msg["Subject"] = f"Your GenBank Verification Code: {otp}"
            msg["From"] = f"{SMTP_FROM_NAME} <{SMTP_USER}>"
            msg["To"] = email

            # Plain text fallback
            msg.set_content(f"""GenBank Online Banking - Verification Code

Hello,

Your One-Time Password (OTP) for account verification is: {otp}

This code is valid for 5 minutes. Please do not share this code with anyone.

GenBank Security Operations
""")

            # Enterprise Branded HTML Email
            html_content = f"""<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"/></head>
<body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; margin: 0; padding: 24px;">
  <div style="max-width: 520px; margin: 0 auto; background: #ffffff; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05);">
    <div style="background: linear-gradient(135deg, #1e1b4b 0%, #312e81 100%); padding: 28px; text-align: center;">
      <h1 style="color: #ffffff; margin: 0; font-size: 22px; font-weight: 800; letter-spacing: 0.5px;">🏦 GenBank (Apna Bank)</h1>
      <p style="color: #cbd5e1; margin: 6px 0 0 0; font-size: 13px;">Enterprise Digital Banking Security Alert</p>
    </div>
    <div style="padding: 32px 28px;">
      <p style="color: #334155; font-size: 15px; line-height: 1.5; margin: 0 0 16px 0;">Hello,</p>
      <p style="color: #475569; font-size: 14px; line-height: 1.6; margin: 0 0 24px 0;">You have requested a secure verification code to register your GenBank retail netbanking account.</p>
      <div style="background: #f1f5f9; border: 2px dashed #cbd5e1; border-radius: 10px; padding: 20px; text-align: center; margin-bottom: 24px;">
        <span style="display: block; font-size: 12px; color: #64748b; font-weight: 600; text-transform: uppercase; letter-spacing: 1px; margin-bottom: 6px;">Your 6-Digit One-Time Password</span>
        <span style="font-family: 'Courier New', Courier, monospace; font-size: 32px; font-weight: 800; color: #4338ca; letter-spacing: 8px;">{otp}</span>
      </div>
      <p style="color: #ef4444; font-size: 13px; margin: 0 0 8px 0; font-weight: 600;">⏱️ Valid for 5 minutes only.</p>
      <p style="color: #64748b; font-size: 12px; line-height: 1.5; margin: 0;">If you did not initiate this request, please disregard this email. Never disclose your banking passwords or OTP codes to anyone, including bank representatives.</p>
    </div>
    <div style="background: #f8fafc; border-top: 1px solid #e2e8f0; padding: 16px 28px; text-align: center;">
      <p style="color: #94a3b8; font-size: 11px; margin: 0;">GenBank Core Banking Platform · RBI Uniform IFSC: GBIN0GENBNK · Pune, India</p>
    </div>
  </div>
</body>
</html>"""
            msg.add_alternative(html_content, subtype="html")

            with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=10) as server:
                server.starttls()
                server.login(SMTP_USER, SMTP_PASSWORD)
                server.send_message(msg)
            print(f"[SMTP] Successfully dispatched live email to {email}")
            dispatched = True
        except Exception as e:
            print(f"[SMTP NOTICE] Live email dispatch skipped ({e}). Falling back to demo mode.")

    return dispatched


@app.post("/api/otp/generate")
def generate_otp(request: OTPRequest) -> Dict[str, Any]:
    """
    Generates and dispatches a 6-digit OTP for the given email.
    Applies rate limiting, sends an authentic email if SMTP is configured,
    and stores the OTP with a 5-minute TTL.
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
    
    email_sent = send_email_otp(request.email, otp)

    if email_sent:
        message = f"Verification code dispatched to {request.email}! Please check your inbox."
    else:
        message = f"Verification code dispatched! For demonstration, your OTP is: {otp}"

    return {
        "status": "success",
        "message": message,
        "email_sent": email_sent,
        "otp": otp
    }


@app.post("/api/otp/verify")
def verify_otp(request: OTPVerify) -> Dict[str, str]:
    """
    Verifies the provided OTP against the stored record.
    Enforces TTL expiration and brute-force attempt limits.
    """
    current_time = time.time()
    record = otp_store.get(request.email)
    
    if not record:
        raise HTTPException(
            status_code=400, 
            detail="No OTP requested for this email or OTP has already expired."
        )
        
    # Check TTL
    if current_time - record["created_at"] > OTP_TTL_SECONDS:
        del otp_store[request.email]
        raise HTTPException(
            status_code=400, 
            detail="OTP has expired. Please request a new one."
        )
        
    # Check attempts
    if record["attempts"] >= MAX_ATTEMPTS:
        del otp_store[request.email]
        raise HTTPException(
            status_code=400, 
            detail="Maximum verification attempts exceeded. Please request a new OTP."
        )
        
    # Verify match securely
    if not secrets.compare_digest(record["otp"], request.otp):
        record["attempts"] += 1
        remaining = MAX_ATTEMPTS - record["attempts"]
        raise HTTPException(
            status_code=400, 
            detail=f"Invalid OTP. {remaining} attempt(s) remaining."
        )
        
    # Success: Invalidate OTP
    del otp_store[request.email]
    return {
        "status": "success", 
        "message": "OTP verified successfully."
    }
