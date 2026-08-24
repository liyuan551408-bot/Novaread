# NovaRead

**NovaRead** is an AI-powered desktop reading and learning application built with **Java, Maven, and JavaFX**.

It is designed to help users understand difficult text, learn vocabulary, and connect ideas while reading. The application combines a traditional desktop reader with AI-assisted features such as translation, contextual vocabulary explanation, sentence analysis, text simplification, semantic retrieval, and interactive knowledge graphs.

## Features

### Reading Experience
- Local text-book library
- Multi-page reading interface
- Reading progress tracking
- Auto-scroll with adjustable speed
- Custom background colour, font colour, and font size
- Persistent reading preferences

### AI Reading Tools
Users can select text while reading and use AI-assisted actions such as:

- Translation between Chinese and English
- Context-aware vocabulary explanation
- Sentence structure analysis
- Text simplification
- AI-generated explanations
- Author-style question answering
- Knowledge graph generation

AI calls are performed in background threads so the JavaFX user interface remains responsive while waiting for results.

### Semantic Retrieval
NovaRead supports embedding-based semantic retrieval.

The application can:

1. Convert text into embeddings.
2. Compare vectors using cosine similarity.
3. Retrieve semantically related content.
4. Provide relevant context to the language model.

This allows the reader to connect ideas across previously processed reading content.

### Interactive Knowledge Graph
Selected text can be transformed into a structured knowledge graph.

The AI extracts:
- Core entities
- Relationships between entities

The graph is rendered interactively inside the reading interface using Cytoscape.js.

### Vocabulary Learning
NovaRead includes a vocabulary learning workflow:

- Save selected words with their original context
- Ask AI for contextual explanations
- Generate multiple-choice review questions
- Review saved vocabulary
- Track learning progress

The vocabulary review feature uses a **spaced repetition (SRS)** approach to adjust review priority based on previous performance.

## Technology Stack

- **Java**
- **Maven**
- **JavaFX**
- **AtlantaFX**
- **Ikonli**
- **Java HttpClient**
- **Gson**
- **SQLite / JDBC**
- **REST APIs**
- **JSON**
- **LLM API integration**
- **Embedding API integration**
- **Cytoscape.js**

## Project Structure

```text
src/
└── main/
    └── java/
        └── com/
            └── scifi/
                └── reader/
                    ├── GuiMain.java
                    ├── WelcomeScreen.java
                    ├── LibraryScreen.java
                    ├── ReaderScreen.java
                    ├── VocabBookScreen.java
                    ├── VocabReviewScreen.java
                    ├── AiClient.java
                    └── DatabaseManager.java
```

### Main Components

**GuiMain.java**  
Application entry point. Manages global application state, screen navigation, database initialisation, and background AI tasks.

**WelcomeScreen.java**  
Initial application screen and entry point to the reading environment.

**LibraryScreen.java**  
Displays the local book library, reading progress, and navigation to vocabulary features.

**ReaderScreen.java**  
Main reading workspace. Handles reading, page navigation, appearance settings, AI tools, auto-scroll, and knowledge graph visualisation.

**VocabBookScreen.java**  
Displays saved vocabulary with its original context and provides AI-assisted contextual explanations.

**VocabReviewScreen.java**  
Provides vocabulary review and multiple-choice exercises based on a spaced repetition workflow.

**AiClient.java**  
Handles communication with external AI services, including LLM requests, embeddings, prompt design, semantic similarity, and AI output processing.

**DatabaseManager.java**  
Handles local application data, including books, reading progress, user preferences, vocabulary, review information, covers, and semantic memory.

## Application Flow

```text
Welcome Screen
      |
      v
Book Library
      |
      v
Reader
  |       |        |
  |       |        +--> AI Reading Tools
  |       |
  |       +-----------> Knowledge Graph
  |
  +-------------------> Vocabulary Book
                           |
                           v
                     Vocabulary Review
```

## AI Workflow

A typical AI-assisted reading request follows this flow:

```text
User selects text
        |
        v
JavaFX event
        |
        v
AiClient
        |
        +--> Prompt / Context Construction
        |
        +--> External LLM API
        |
        v
Response Processing
        |
        v
JavaFX UI Update
```

For semantic retrieval:

```text
Reading Content
      |
      v
Embedding API
      |
      v
Vector Representation
      |
      v
Cosine Similarity
      |
      v
Relevant Context
      |
      v
LLM Response
```

## Running the Project

### Requirements

You will need:

- Java Development Kit
- Maven
- Internet access for AI API features

### Build

From the project root:

```bash
mvn clean package
```

### Run

Depending on the Maven configuration used by the project, run the configured JavaFX application from your IDE or Maven.

For example, if the JavaFX Maven plugin is configured:

```bash
mvn javafx:run
```

The main application class is:

```text
com.scifi.reader.GuiMain
```

### API Configuration

AI features require a Zhipu API key. Configure it with the `ZHIPU_API_KEY` environment variable before running the app.

On Windows PowerShell:

```powershell
setx ZHIPU_API_KEY "your-api-key"
```

For local development, you can also create an ignored `.env` file in the project root:

```text
ZHIPU_API_KEY=your-api-key
```

## Local Books

NovaRead imports local `.txt` books from the project's `book` directory.

Example:

```text
project-root/
├── book/
│   ├── book1.txt
│   └── book2.txt
├── covers/
└── src/
```

The application attempts to read text using several common encodings to support different book files.

Optional `.jpg` or `.png` cover images can be placed in the `covers` directory.

## Data Storage

NovaRead uses local SQLite databases to store application data such as:

- Books and paragraphs
- Reading progress
- Reading time
- User display settings
- Vocabulary
- Review progress
- Semantic memory
- Book covers

## Security Note

**Do not commit API keys to a public GitHub repository.**

Before publishing the project, AI credentials should be removed from the Java source code and loaded from an environment variable or another secure configuration method.

Recommended approach:

```java
String apiKey = System.getenv("ZHIPU_API_KEY");
```

Then configure the environment variable on your local machine rather than storing the key directly in the repository.

If an API key has already been committed publicly, revoke it and create a new one.

## What I Learned

Building NovaRead gave me practical experience with:

- Structuring a Java desktop application
- Developing multi-screen JavaFX interfaces
- Managing application state and user workflows
- Calling REST APIs from Java
- Processing JSON responses
- Integrating LLM and embedding services
- Running long tasks without blocking the UI
- Debugging application and API behaviour
- Designing AI prompts for different user tasks
- Implementing semantic retrieval
- Building interactive AI-supported features
- Improving features through testing and iteration

## Future Improvements

Possible next steps include:

- Move all API configuration to environment variables
- Improve exception handling and user-facing error messages
- Add automated tests
- Add more document formats such as PDF and EPUB
- Improve semantic retrieval and chunking
- Add configurable AI providers
- Improve accessibility and keyboard navigation
- Package the application for easier installation
- Add stronger separation between UI, application logic, and data access

## Project Status

NovaRead is currently a working prototype and is continuing to be improved.

The project was created to explore how **Java application development and generative AI can be combined to build a practical learning tool**.

---

**Developer:** Andrew (Yinchen) Guo
