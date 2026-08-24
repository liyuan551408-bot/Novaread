package com.scifi.reader;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:library.db";
    private static final String COVERS_DB_URL = "jdbc:sqlite:covers.db";
    private static final int PARAGRAPHS_PER_PAGE = 25;

    // ========================= Main database initialization =========================
    public static void initDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS books (id TEXT PRIMARY KEY, title TEXT, total_pages INTEGER)");
            stmt.execute("CREATE TABLE IF NOT EXISTS paragraphs (id INTEGER PRIMARY KEY AUTOINCREMENT, book_id TEXT, page_number INTEGER, content TEXT, FOREIGN KEY (book_id) REFERENCES books (id))");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_book_page ON paragraphs(book_id, page_number)");
            stmt.execute("CREATE TABLE IF NOT EXISTS flashcards (word TEXT PRIMARY KEY, context TEXT, lookups INTEGER, dwell_time REAL, difficulty REAL)");

            // Dynamically upgrade flashcard quiz fields
            try { stmt.execute("ALTER TABLE flashcards ADD COLUMN last_review_time INTEGER DEFAULT 0"); } catch (SQLException ignore) {}
            try { stmt.execute("ALTER TABLE flashcards ADD COLUMN streak INTEGER DEFAULT 0"); } catch (SQLException ignore) {}
            try { stmt.execute("ALTER TABLE flashcards ADD COLUMN opt_a TEXT"); } catch (SQLException ignore) {}
            try { stmt.execute("ALTER TABLE flashcards ADD COLUMN opt_b TEXT"); } catch (SQLException ignore) {}
            try { stmt.execute("ALTER TABLE flashcards ADD COLUMN opt_c TEXT"); } catch (SQLException ignore) {}
            try { stmt.execute("ALTER TABLE flashcards ADD COLUMN opt_d TEXT"); } catch (SQLException ignore) {}
            try { stmt.execute("ALTER TABLE flashcards ADD COLUMN correct_ans TEXT"); } catch (SQLException ignore) {}

            stmt.execute("CREATE TABLE IF NOT EXISTS memory_chunks (id INTEGER PRIMARY KEY AUTOINCREMENT, book_id TEXT, chunk_text TEXT, vector_json TEXT)");
            try { stmt.execute("ALTER TABLE memory_chunks ADD COLUMN vector_json TEXT"); } catch (SQLException ignore) {}

            stmt.execute("CREATE TABLE IF NOT EXISTS user_books (" +
                    "book_id TEXT PRIMARY KEY, " +
                    "in_bookshelf INTEGER DEFAULT 0, " +
                    "last_read_page INTEGER DEFAULT 1, " +
                    "total_read_time REAL DEFAULT 0, " +
                    "prompt_ignored INTEGER DEFAULT 0)");

            // Add user settings table
            stmt.execute("CREATE TABLE IF NOT EXISTS user_settings (key TEXT PRIMARY KEY, value TEXT)");

            System.out.println("Main database initialized.");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // ========================= User settings methods =========================
    public static String getSetting(String key, String defaultValue) {
        String sql = "SELECT value FROM user_settings WHERE key = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("value");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return defaultValue;
    }

    public static void saveSetting(String key, String value) {
        String sql = "INSERT OR REPLACE INTO user_settings (key, value) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ========================= Cover database initialization =========================
    public static void initCoverDatabase() {
        try (Connection conn = DriverManager.getConnection(COVERS_DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS covers (" +
                    "book_id TEXT PRIMARY KEY, " +
                    "image_data BLOB, " +
                    "mime_type TEXT, " +
                    "import_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            System.out.println("Cover database initialized.");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // ========================= Cover management methods =========================
    public static void saveCover(String bookId, byte[] imageData, String mimeType) {
        if (bookId == null || imageData == null) return;
        String sql = "INSERT OR REPLACE INTO covers (book_id, image_data, mime_type) VALUES (?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(COVERS_DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, bookId);
            pstmt.setBytes(2, imageData);
            pstmt.setString(3, mimeType);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static byte[] getCoverData(String bookId) {
        if (bookId == null) return null;
        String sql = "SELECT image_data FROM covers WHERE book_id = ?";
        try (Connection conn = DriverManager.getConnection(COVERS_DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, bookId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getBytes("image_data");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean hasCover(String bookId) {
        return getCoverData(bookId) != null;
    }

    public static boolean importCoverFromFile(String bookId, String coverFolder) {
        if (bookId == null) return false;
        String cleanTitle = getBookTitle(bookId);
        File imgJpg = new File(coverFolder + cleanTitle + ".jpg");
        File imgPng = new File(coverFolder + cleanTitle + ".png");
        File imgFile = null;
        String mimeType = null;
        if (imgJpg.exists()) {
            imgFile = imgJpg;
            mimeType = "image/jpeg";
        } else if (imgPng.exists()) {
            imgFile = imgPng;
            mimeType = "image/png";
        }
        if (imgFile != null && imgFile.isFile()) {
            try {
                byte[] data = Files.readAllBytes(imgFile.toPath());
                saveCover(bookId, data, mimeType);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public static void importBooksFromFolder(String folderPath) {
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) return;

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            for (File file : folder.listFiles((dir, name) -> name.endsWith(".txt"))) {
                String bookId = file.getName().replace(".txt", "");
                PreparedStatement checkStmt = conn.prepareStatement("SELECT id FROM books WHERE id = ?");
                checkStmt.setString(1, bookId);
                if (checkStmt.executeQuery().next()) {
                    System.out.println("Book already exists, skipping: " + bookId);
                    continue;
                }

                List<String> validParagraphs = null;
                String[] encodings = {"UTF-8", "GBK", "GB2312", "GB18030", "ISO-8859-1"};
                Exception lastException = null;

                for (String encoding : encodings) {
                    try (BufferedReader reader = Files.newBufferedReader(file.toPath(), Charset.forName(encoding))) {
                        validParagraphs = new ArrayList<>();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.trim().isEmpty()) validParagraphs.add(line.trim());
                        }
                        System.out.println("Successfully read with encoding " + encoding + ": " + file.getName());
                        break;
                    } catch (IOException e) {
                        lastException = e;
                    }
                }

                if (validParagraphs == null) {
                    System.err.println("All encoding attempts failed: " + file.getName());
                    if (lastException != null) lastException.printStackTrace();
                    continue;
                }

                if (validParagraphs.isEmpty()) {
                    System.err.println("File has no valid paragraphs, skipping: " + file.getName());
                    continue;
                }

                int totalPages = Math.max(1, (validParagraphs.size() + PARAGRAPHS_PER_PAGE - 1) / PARAGRAPHS_PER_PAGE);
                try {
                    PreparedStatement insertBook = conn.prepareStatement("INSERT INTO books (id, title, total_pages) VALUES (?, ?, ?)");
                    insertBook.setString(1, bookId);
                    insertBook.setString(2, bookId);
                    insertBook.setInt(3, totalPages);
                    insertBook.executeUpdate();

                    PreparedStatement insertPara = conn.prepareStatement("INSERT INTO paragraphs (book_id, page_number, content) VALUES (?, ?, ?)");
                    conn.setAutoCommit(false);
                    for (int i = 0; i < validParagraphs.size(); i++) {
                        insertPara.setString(1, bookId);
                        insertPara.setInt(2, (i / PARAGRAPHS_PER_PAGE) + 1);
                        insertPara.setString(3, validParagraphs.get(i));
                        insertPara.addBatch();
                    }
                    insertPara.executeBatch();
                    conn.commit();
                    conn.setAutoCommit(true);
                    System.out.println("Import succeeded: " + bookId);
                } catch (SQLException e) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        rollbackEx.printStackTrace();
                    }
                    System.err.println("Database operation failed: " + file.getName() + ", reason: " + e.getMessage());
                    e.printStackTrace();
                    conn.setAutoCommit(true);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static List<BookRank> getRankedBooks() {
        List<BookRank> rankedList = new ArrayList<>();
        double maxReadTime = 1.0;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            ResultSet maxRs = stmt.executeQuery("SELECT MAX(total_read_time) as max_time FROM user_books");
            if (maxRs.next() && maxRs.getDouble("max_time") > 0) maxReadTime = maxRs.getDouble("max_time");

            String sql = "SELECT b.id, b.total_pages, u.in_bookshelf, u.last_read_page, u.total_read_time " +
                    "FROM books b LEFT JOIN user_books u ON b.id = u.book_id";
            ResultSet rs = stmt.executeQuery(sql);

            while (rs.next()) {
                String bookId = rs.getString("id");
                int totalPages = rs.getInt("total_pages");
                boolean isStarred = rs.getInt("in_bookshelf") == 1;
                int lastPage = Math.max(1, rs.getInt("last_read_page"));
                double readTime = rs.getDouble("total_read_time");

                double f1Score = isStarred ? 40.0 : 0.0;
                double progressRate = Math.min(1.0, (double) lastPage / totalPages);
                double f2Score = progressRate * 35.0;
                double engagementRate = readTime / maxReadTime;
                double f3Score = engagementRate * 25.0;

                int finalScore = (int) Math.round(f1Score + f2Score + f3Score);
                if (finalScore == 0) finalScore = 5;
                rankedList.add(new BookRank(bookId, finalScore));
            }
            Collections.sort(rankedList);
        } catch (SQLException e) { e.printStackTrace(); }
        return rankedList;
    }

    public static List<String> getPageContent(String bookId, int pageNumber) {
        List<String> paragraphs = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement("SELECT content FROM paragraphs WHERE book_id = ? AND page_number = ? ORDER BY id ASC")) {
            stmt.setString(1, bookId); stmt.setInt(2, pageNumber);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) paragraphs.add(rs.getString("content"));
        } catch (SQLException e) { e.printStackTrace(); }
        return paragraphs;
    }

    public static int getTotalPages(String bookId) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement("SELECT total_pages FROM books WHERE id = ?")) {
            stmt.setString(1, bookId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("total_pages");
        } catch (SQLException e) { e.printStackTrace(); }
        return 1;
    }

    public static void initUserBook(String bookId) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement("INSERT OR IGNORE INTO user_books (book_id) VALUES (?)")) {
            stmt.setString(1, bookId); stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static int getLastReadPage(String bookId) {
        initUserBook(bookId);
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement("SELECT last_read_page FROM user_books WHERE book_id = ?")) {
            stmt.setString(1, bookId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("last_read_page");
        } catch (SQLException e) { e.printStackTrace(); }
        return 1;
    }

    public static void setInBookshelf(String bookId, boolean inShelf) {
        initUserBook(bookId);
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement("UPDATE user_books SET in_bookshelf = ? WHERE book_id = ?")) {
            stmt.setInt(1, inShelf ? 1 : 0); stmt.setString(2, bookId); stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static void updateProgress(String bookId, int page, double timeSpent) {
        initUserBook(bookId);
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement("UPDATE user_books SET last_read_page = ?, total_read_time = total_read_time + ? WHERE book_id = ?")) {
            stmt.setInt(1, page); stmt.setDouble(2, timeSpent); stmt.setString(3, bookId); stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static String getBookTitle(String bookId) {
        String title = bookId;
        String sql = "SELECT title FROM books WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, bookId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String dbTitle = rs.getString("title");
                if (dbTitle != null && !dbTitle.trim().isEmpty()) title = dbTitle;
            }
        } catch (SQLException e) {
            System.out.println("Failed to get book title: " + e.getMessage());
        }
        return title;
    }

    public static String saveFlashcard(String word, String context, double dwellTime) {
        String sql = "INSERT INTO flashcards (word, context, lookups, dwell_time, difficulty, last_review_time, streak) VALUES (?, ?, 1, ?, ?, 0, 0) ON CONFLICT(word) DO UPDATE SET lookups = lookups + 1, dwell_time = dwell_time + excluded.dwell_time, difficulty = (lookups + 1) / (dwell_time + excluded.dwell_time)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, word); stmt.setString(2, context); stmt.setDouble(3, dwellTime); stmt.setDouble(4, 1.0 / dwellTime); stmt.executeUpdate();

            asyncGenerateAndStoreQuiz(word, context);

            PreparedStatement query = conn.prepareStatement("SELECT difficulty FROM flashcards WHERE word = ?");
            query.setString(1, word); ResultSet rs = query.executeQuery();
            if (rs.next()) return String.format("SRS update succeeded!\nWord: %s\nThe quiz bank is being built in the background.", word);
        } catch (SQLException e) { return "SRS save failed: " + e.getMessage(); }
        return "Saved.";
    }

    private static void asyncGenerateAndStoreQuiz(String word, String context) {
        new Thread(() -> {
            String[] quiz = AiClient.generateQuizOptions(word, context);
            String updateSql = "UPDATE flashcards SET opt_a=?, opt_b=?, opt_c=?, opt_d=?, correct_ans=? WHERE word=?";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                stmt.setString(1, quiz[0]); stmt.setString(2, quiz[1]);
                stmt.setString(3, quiz[2]); stmt.setString(4, quiz[3]);
                stmt.setString(5, quiz[4]); stmt.setString(6, word);
                stmt.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        }).start();
    }

    public static String saveMemoryChunk(String bookId, String text) {
        double[] vector = AiClient.getEmbedding(text);
        if (vector.length == 0) return "Memory injection failed: vector generation failed. Check network or configuration.";
        String vectorJson = new Gson().toJson(vector);

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO memory_chunks (book_id, chunk_text, vector_json) VALUES (?, ?, ?)")) {
            stmt.setString(1, bookId != null ? bookId : "Unknown book");
            stmt.setString(2, text);
            stmt.setString(3, vectorJson);
            stmt.executeUpdate();
            return "Memory saved: the chunk has been mapped into the semantic memory network.";
        } catch (SQLException e) { return "Memory save failed: " + e.getMessage(); }
    }

    public static String getSemanticContext(String currentText, int topK) {
        double[] queryVector = AiClient.getEmbedding(currentText);
        if (queryVector.length == 0) return "Memory retrieval failed: unable to generate a semantic probe.";

        class MemoryHit implements Comparable<MemoryHit> {
            String content; double score;
            MemoryHit(String c, double s) { this.content = c; this.score = s; }
            @Override public int compareTo(MemoryHit o) { return Double.compare(o.score, this.score); }
        }

        PriorityQueue<MemoryHit> hits = new PriorityQueue<>();
        Gson gson = new Gson();

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT book_id, chunk_text, vector_json FROM memory_chunks")) {

            while (rs.next()) {
                String vectorJson = rs.getString("vector_json");
                if (vectorJson == null || vectorJson.isEmpty()) continue;
                double[] memVector = gson.fromJson(vectorJson, double[].class);
                double similarity = AiClient.cosineSimilarity(queryVector, memVector);

                if (similarity > 0.45) {
                    String memStr = "[" + rs.getString("book_id") + "]: " + rs.getString("chunk_text");
                    hits.add(new MemoryHit(memStr, similarity));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }

        StringBuilder contextBuilder = new StringBuilder();
        int count = 0;
        while (!hits.isEmpty() && count < topK) {
            MemoryHit hit = hits.poll();
            contextBuilder.append(count + 1).append(". ").append(hit.content)
                    .append(" (semantic match: ").append(String.format("%.1f%%", hit.score * 100)).append(")\n");
            count++;
        }

        return contextBuilder.length() > 0 ? contextBuilder.toString() : "Semantic scan finished. No related memories were found.";
    }

    public static boolean isInBookshelf(String bookId) {
        initUserBook(bookId);
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement("SELECT in_bookshelf FROM user_books WHERE book_id = ?")) {
            stmt.setString(1, bookId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("in_bookshelf") == 1;
        } catch (SQLException e) { e.printStackTrace(); }
        return false;
    }

    public static class BookRank implements Comparable<BookRank> {
        public String bookId; public int score;
        public BookRank(String bookId, int score) { this.bookId = bookId; this.score = score; }
        @Override public int compareTo(BookRank other) { return Integer.compare(other.score, this.score); }
    }

    public static class Flashcard {
        public String word;
        public String context;
        public double difficulty;
        public long lastReviewTime;
        public int streak;
        public double urgency;
        public String optA, optB, optC, optD, correctAns;

        public Flashcard(String word, String context, double difficulty, long lastReviewTime, int streak,
                         String optA, String optB, String optC, String optD, String correctAns) {
            this.word = word;
            this.context = context;
            this.difficulty = difficulty;
            this.lastReviewTime = lastReviewTime;
            this.streak = streak;
            this.urgency = calculateUrgency();
            this.optA = optA; this.optB = optB; this.optC = optC; this.optD = optD; this.correctAns = correctAns;
        }

        private double calculateUrgency() {
            if (lastReviewTime == 0) return 999.0;
            long timeElapsed = System.currentTimeMillis() - lastReviewTime;
            long[] intervals = { 5 * 60 * 1000L, 12 * 60 * 60 * 1000L, 24 * 60 * 60 * 1000L,
                    3 * 24 * 60 * 60 * 1000L, 7 * 24 * 60 * 60 * 1000L, 15 * 24 * 60 * 60 * 1000L };
            int safeStreak = Math.min(streak, intervals.length - 1);
            long targetInterval = (long) (intervals[safeStreak] * difficulty);
            return (double) timeElapsed / targetInterval;
        }
    }

    public static List<Flashcard> getFlashcards() {
        List<Flashcard> cards = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT word, context, difficulty, last_review_time, streak, opt_a, opt_b, opt_c, opt_d, correct_ans FROM flashcards")) {
            while (rs.next()) {
                cards.add(new Flashcard(
                        rs.getString("word"), rs.getString("context"),
                        rs.getDouble("difficulty"), rs.getLong("last_review_time"), rs.getInt("streak"),
                        rs.getString("opt_a"), rs.getString("opt_b"), rs.getString("opt_c"), rs.getString("opt_d"), rs.getString("correct_ans")
                ));
            }
            cards.sort((a, b) -> Double.compare(b.urgency, a.urgency));
        } catch (SQLException e) { e.printStackTrace(); }
        return cards;
    }

    public static void submitReview(String word, boolean isCorrect) {
        String querySql = "SELECT streak, difficulty FROM flashcards WHERE word = ?";
        String updateSql = "UPDATE flashcards SET streak = ?, difficulty = ?, last_review_time = ? WHERE word = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement queryStmt = conn.prepareStatement(querySql);
             PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {

            queryStmt.setString(1, word);
            ResultSet rs = queryStmt.executeQuery();
            if (rs.next()) {
                int streak = rs.getInt("streak");
                double difficulty = rs.getDouble("difficulty");

                if (isCorrect) {
                    streak++;
                    difficulty = Math.max(0.5, difficulty * 0.9);
                } else {
                    streak = 0;
                    difficulty = Math.min(3.0, difficulty * 1.2);
                }

                updateStmt.setInt(1, streak);
                updateStmt.setDouble(2, difficulty);
                updateStmt.setLong(3, System.currentTimeMillis());
                updateStmt.setString(4, word);
                updateStmt.executeUpdate();
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static void deleteFlashcard(String word) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM flashcards WHERE word = ?")) {
            stmt.setString(1, word); stmt.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
}