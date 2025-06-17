from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from openai import OpenAI
from typing import Optional
import os
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

app = FastAPI(title="Sensei AI Service", version="1.0.0")

# Initialize OpenAI client
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))

# Request/Response models
class SummaryRequest(BaseModel):
    chunk: str
    query: str
    bookTitle: str
    chapterTitle: Optional[str] = None

class SummaryResponse(BaseModel):
    contextSummary: str
    relevanceExplanation: str

@app.get("/")
async def root():
    return {"message": "Sensei AI Service is running"}

@app.post("/generate-summary", response_model=SummaryResponse)
async def generate_summary(request: SummaryRequest):
    """
    Generate context summary and relevance explanation for a search result.
    This mirrors the functionality of AISummaryService.java
    """
    try:
        # Create the Japanese prompt (matching the fixed Java version)
        system_prompt = """あなたは仏法哲学、特に池田大作先生の著作に精通した座談会のリーダーです。
読者が本の文章の文脈と関連性を理解できるよう支援することがあなたの役割です。

文章と検索クエリが与えられたら、以下を提供してください：
1. この文章で何が起きているかを説明する簡潔な文脈の要約
2. なぜこの文章が検索クエリと関連しているかの説明

回答は簡潔で役立つものにしてください。教えを読者にとってアクセスしやすく、
意味のあるものにすることに焦点を当ててください。温かく、支援的なトーンで書いてください。

必ず日本語で回答してください。

回答は以下の形式で：
文脈: [文章で何が起きているかの簡潔な説明]
関連性: [なぜこの文章が検索クエリと関連しているか]"""
        
        chapter_info = request.chapterTitle if request.chapterTitle else "不明"
        
        user_prompt = f"""書籍: {request.bookTitle}
章: {chapter_info}
検索クエリ: {request.query}

文章:
{request.chunk}

この文章の文脈の要約と関連性の説明を提供してください。"""

        # Call OpenAI API
        response = client.chat.completions.create(
            model="gpt-4-1106-preview",
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt}
            ],
            max_tokens=300,
            temperature=0.7
        )
        
        # Parse the response
        content = response.choices[0].message.content
        
        # Extract context and relevance using Japanese labels
        context_summary = ""
        relevance_explanation = ""
        
        if "文脈:" in content and "関連性:" in content:
            parts = content.split("関連性:")
            context_part = parts[0].replace("文脈:", "").strip()
            relevance_part = parts[1].strip()
            
            context_summary = context_part
            relevance_explanation = relevance_part
        else:
            # Fallback if format is not as expected
            context_summary = content
            relevance_explanation = "この文章は検索クエリと関連しています。"
        
        return SummaryResponse(
            contextSummary=context_summary,
            relevanceExplanation=relevance_explanation
        )
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {"status": "healthy"}