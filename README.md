# 文学検索 (Bungaku Kensaku) - AI Literature Search Engine

### <em>Video Walkthrough: </em>https://youtu.be/gMDcrrX8Tug
<a href="https://youtu.be/gMDcrrX8Tug">
<img width="3260" height="1926" alt="Bungaku Kensaku Screen" src="https://github.com/user-attachments/assets/c93015a8-bb64-4cf1-8018-feda711c6f2a"></a>

## Summary
A multilingual AI-powered semantic search engine for Japanese literature, featuring intelligent context-aware search results and natural language understanding.

## Features

- **Semantic Search**: AI-powered understanding of queries in both Japanese and English
- **Context-Aware Results**: Each result includes intelligent summaries explaining relevance and context
- **Multilingual Support**: Search Japanese literature with queries in multiple languages
- **Intuitive UI**: Clean, scholarly interface inspired by traditional Japanese design
- **Vector Database**: Uses embeddings for sophisticated content matching beyond keyword search
- **Scalable Architecture**: Designed to grow from 5 to 100+ books seamlessly

## Architecture

### Technology Stack
- **Backend**: Java Spring Boot
- **Frontend**: Thymeleaf templates with responsive CSS/JavaScript
- **Database**: PostgreSQL (metadata & book information)
- **Vector Search**: Pinecone vector database
- **AI Integration**: OpenAI embeddings and ChatGPT for intelligent summaries
- **Cloud**: AWS EC2

### System Design
```
Frontend (Thymeleaf) → Spring Boot API → Vector Search Service → PostgreSQL
                                      ↘ Pinecone Vector DB
                                      ↘ OpenAI API (embeddings + summaries)
```

## Getting Started

### Prerequisites
- Java 17+
- Maven 3.6+
- PostgreSQL 12+
- API keys for:
- OpenAI (for embeddings and AI summaries)
- Pinecone (for vector search)

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/bungaku-kensaku.git
   cd bungaku-kensaku
   ```

2. **Set up configuration**
   ```bash
   # Copy example configuration
   cp src/main/resources/application-example.properties src/main/resources/application-local.properties
   
   # Edit application-local.properties with your actual credentials
   ```

3. **Configure environment variables**
   ```bash
   export OPENAI_API_KEY="your-openai-api-key"
   export PINECONE_API_KEY="your-pinecone-api-key"
   export DB_PASSWORD="your-database-password"
   ```

4. **Set up PostgreSQL database**
   ```sql
   CREATE DATABASE bungaku_kensaku_db;
   CREATE USER your_username WITH PASSWORD 'your_password';
   GRANT ALL PRIVILEGES ON DATABASE bungaku_kensaku_db TO your_username;
   ```

5. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

6. **Access the application**
   - Navigate to `http://localhost:8080`
   - Default demo credentials:
     - Username: `demo`
     - Password: `changeme`
   - Or configure your own via environment variables:
     ```bash
     export DEMO_USERNAME=your-username
     export DEMO_PASSWORD=your-password
     ```

## 📁 Project Structure

```
src/main/java/com/senseisearch/
├── controller/        # REST API endpoints
├── service/           # Business logic (search, AI, document processing)
├── repository/        # JPA data access layer
├── model/             # Entity classes
├── config/            # Configuration classes
└── util/              # Utility classes

src/main/resources/
├── templates/               # Thymeleaf HTML templates
├── static/                  # CSS, JavaScript, images
└── application*.properties  # Configuration files
```

## How It Works

### Document Processing Pipeline
1. **PDF Upload**: Documents are uploaded and stored in PostgreSQL
2. **Text Extraction**: Content is extracted and chunked (500 tokens with overlap)
3. **Embedding Generation**: Each chunk is converted to vector embeddings using OpenAI
4. **Vector Storage**: Embeddings are indexed in Pinecone for fast similarity search
5. **Metadata Storage**: Book information and chunk metadata stored in PostgreSQL

### Search Process
1. **Query Processing**: User query is converted to embeddings
2. **Similarity Search**: Pinecone finds most relevant text chunks
3. **Context Assembly**: Retrieved chunks are enriched with book/chapter context
4. **AI Summary**: OpenAI generates intelligent explanations for each result
5. **Result Presentation**: Clean, organized results with context and relevance explanations

## Use Cases

- **Academic Research**: Deep exploration of philosophical and literary texts
- **Study Groups**: Finding relevant passages for discussion topics
- **Cross-Reference Search**: Discovering connections between different works
- **Multilingual Access**: Non-Japanese speakers accessing Japanese literature
- **Contextual Learning**: Understanding passages within their broader narrative context



