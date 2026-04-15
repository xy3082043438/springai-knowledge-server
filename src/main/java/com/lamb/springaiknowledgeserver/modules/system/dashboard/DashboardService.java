package com.lamb.springaiknowledgeserver.modules.system.dashboard;

import com.lamb.springaiknowledgeserver.modules.system.dashboard.DashboardDistributionItemResponse;
import com.lamb.springaiknowledgeserver.modules.system.dashboard.DashboardOverviewResponse;
import com.lamb.springaiknowledgeserver.modules.system.dashboard.DashboardResponse;
import com.lamb.springaiknowledgeserver.modules.system.dashboard.DashboardTrendPointResponse;
import com.lamb.springaiknowledgeserver.modules.system.dashboard.DashboardWordCloudItemResponse;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentRepository;
import com.lamb.springaiknowledgeserver.modules.knowledge.document.DocumentTypeStatRow;
import com.lamb.springaiknowledgeserver.modules.aiqa.chat.QaLogRepository;
import com.lamb.springaiknowledgeserver.modules.aiqa.chat.QaQuestionActivityRow;
import com.lamb.springaiknowledgeserver.modules.system.role.RoleRepository;
import com.lamb.springaiknowledgeserver.modules.system.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final Pattern ENGLISH_TOKEN_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9+#._-]{1,31}");
    private static final Set<String> ENGLISH_STOP_WORDS = Set.of(
        "how", "what", "why", "when", "where", "who", "can", "could", "would", "should",
        "the", "and", "for", "with", "from", "this", "that", "please", "help"
    );
    private static final List<String> DOMAIN_KEYWORDS = List.of(
        "知识库", "文档", "权限", "角色", "用户", "问答", "检索", "向量", "模型", "配置",
        "登录", "接口", "反馈", "日志", "仪表盘", "大屏", "索引", "重排", "上传", "下载",
        "Token", "JWT"
    );
    private static final List<String> QUESTION_PREFIXES = List.of(
        "请问一下", "请问", "问一下", "问下", "想问一下", "想问", "请帮我", "帮我",
        "如何", "怎么", "怎样", "为什么", "是否", "能否", "有没有", "可以"
    );
    private static final List<String> QUESTION_SUFFIXES = List.of(
        "是什么意思", "是什么", "有哪些", "怎么做", "怎么办", "吗", "呢", "呀", "啊", "吧", "么", "嘛"
    );

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DocumentRepository documentRepository;
    private final QaLogRepository qaLogRepository;

    public DashboardResponse getDashboard(int trendDays, int keywordDays, int keywordLimit) {
        int safeTrendDays = clamp(trendDays, 1, 60);
        int safeKeywordDays = clamp(keywordDays, 1, 90);
        int safeKeywordLimit = clamp(keywordLimit, 5, 50);

        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        Instant trendStart = today.minusDays(safeTrendDays - 1L).atStartOfDay(zone).toInstant();
        Instant keywordStart = today.minusDays(safeKeywordDays - 1L).atStartOfDay(zone).toInstant();
        Instant earliestStart = trendStart.isBefore(keywordStart) ? trendStart : keywordStart;
        Instant todayStart = today.atStartOfDay(zone).toInstant();
        Instant tomorrowStart = today.plusDays(1L).atStartOfDay(zone).toInstant();

        List<QaQuestionActivityRow> questionActivities = qaLogRepository.findQuestionActivitiesSince(earliestStart);

        return new DashboardResponse(
            Instant.now(),
            safeTrendDays,
            safeKeywordDays,
            safeKeywordLimit,
            buildOverview(todayStart, tomorrowStart),
            buildTrend(questionActivities, today, safeTrendDays, zone),
            buildHotKeywords(questionActivities, keywordStart, safeKeywordLimit),
            buildDocumentTypeDistribution(documentRepository.findAllTypeStats())
        );
    }

    private DashboardOverviewResponse buildOverview(Instant todayStart, Instant tomorrowStart) {
        return new DashboardOverviewResponse(
            userRepository.count(),
            roleRepository.count(),
            documentRepository.count(),
            qaLogRepository.count(),
            qaLogRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(todayStart, tomorrowStart)
        );
    }

    private List<DashboardTrendPointResponse> buildTrend(
        List<QaQuestionActivityRow> activities,
        LocalDate today,
        int trendDays,
        ZoneId zone
    ) {
        LocalDate startDate = today.minusDays(trendDays - 1L);
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        for (int index = 0; index < trendDays; index++) {
            counts.put(startDate.plusDays(index), 0L);
        }
        for (QaQuestionActivityRow activity : activities) {
            if (activity.getCreatedAt() == null) {
                continue;
            }
            LocalDate date = activity.getCreatedAt().atZone(zone).toLocalDate();
            if (counts.containsKey(date)) {
                counts.put(date, counts.get(date) + 1);
            }
        }
        List<DashboardTrendPointResponse> result = new ArrayList<>(counts.size());
        for (Map.Entry<LocalDate, Long> entry : counts.entrySet()) {
            result.add(new DashboardTrendPointResponse(entry.getKey().toString(), entry.getValue()));
        }
        return result;
    }

    private List<DashboardWordCloudItemResponse> buildHotKeywords(
        List<QaQuestionActivityRow> activities,
        Instant keywordStart,
        int keywordLimit
    ) {
        Map<String, Long> counts = new HashMap<>();
        for (QaQuestionActivityRow activity : activities) {
            if (activity.getCreatedAt() == null || activity.getCreatedAt().isBefore(keywordStart)) {
                continue;
            }
            for (String token : extractKeywords(activity.getQuestion())) {
                counts.merge(token, 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
            .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey))
            .limit(keywordLimit)
            .map(entry -> new DashboardWordCloudItemResponse(entry.getKey(), entry.getValue()))
            .toList();
    }

    private Set<String> extractKeywords(String question) {
        if (question == null || question.isBlank()) {
            return Set.of();
        }
        String normalized = question.trim();
        String lowerCaseQuestion = normalized.toLowerCase(Locale.ROOT);
        Set<String> keywords = new LinkedHashSet<>();

        // Prefer a stable domain vocabulary so the word cloud is readable without extra NLP dependencies.
        for (String keyword : DOMAIN_KEYWORDS) {
            if (lowerCaseQuestion.contains(keyword.toLowerCase(Locale.ROOT))) {
                keywords.add(keyword);
            }
        }

        Matcher matcher = ENGLISH_TOKEN_PATTERN.matcher(lowerCaseQuestion);
        while (matcher.find()) {
            String token = matcher.group();
            if (!ENGLISH_STOP_WORDS.contains(token)) {
                keywords.add(token);
            }
        }

        if (keywords.isEmpty()) {
            String phrase = normalizeQuestionPhrase(normalized);
            if (phrase != null) {
                keywords.add(phrase);
            }
        }
        return keywords;
    }

    private String normalizeQuestionPhrase(String question) {
        String[] segments = question
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace('，', ' ')
            .replace('。', ' ')
            .replace('？', ' ')
            .replace('！', ' ')
            .replace('、', ' ')
            .replace('；', ' ')
            .replace('：', ' ')
            .split("\\s+");

        String best = null;
        for (String segment : segments) {
            String cleaned = trimQuestionPhrase(segment);
            if (cleaned.length() < 2 || cleaned.length() > 12 || !containsHan(cleaned)) {
                continue;
            }
            if (best == null || cleaned.length() > best.length()) {
                best = cleaned;
            }
        }
        return best;
    }

    private String trimQuestionPhrase(String value) {
        String result = value == null ? "" : value.trim();
        boolean changed;
        do {
            changed = false;
            for (String prefix : QUESTION_PREFIXES) {
                if (result.startsWith(prefix) && result.length() > prefix.length()) {
                    result = result.substring(prefix.length()).trim();
                    changed = true;
                }
            }
            for (String suffix : QUESTION_SUFFIXES) {
                if (result.endsWith(suffix) && result.length() > suffix.length()) {
                    result = result.substring(0, result.length() - suffix.length()).trim();
                    changed = true;
                }
            }
        } while (changed);
        return result.replace("一下", "").trim();
    }

    private boolean containsHan(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.UnicodeScript.of(value.charAt(index)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private List<DashboardDistributionItemResponse> buildDocumentTypeDistribution(List<DocumentTypeStatRow> rows) {
        Map<String, Long> counts = new HashMap<>();
        for (DocumentTypeStatRow row : rows) {
            String type = classifyDocumentType(row.getContentType(), row.getFileName());
            counts.merge(type, 1L, Long::sum);
        }
        return counts.entrySet().stream()
            .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey))
            .map(entry -> new DashboardDistributionItemResponse(
                entry.getKey(),
                documentTypeLabel(entry.getKey()),
                entry.getValue()
            ))
            .toList();
    }

    private String classifyDocumentType(String contentType, String fileName) {
        String normalizedContentType = normalizeContentType(contentType);
        if ("application/pdf".equals(normalizedContentType)) {
            return "PDF";
        }
        if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(normalizedContentType)) {
            return "DOCX";
        }
        if ("application/vnd.openxmlformats-officedocument.presentationml.presentation".equals(normalizedContentType)) {
            return "PPTX";
        }
        if ("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(normalizedContentType)) {
            return "XLSX";
        }
        if ("text/markdown".equals(normalizedContentType) || "text/x-markdown".equals(normalizedContentType)) {
            return "MD";
        }
        if ("text/html".equals(normalizedContentType) || "application/xhtml+xml".equals(normalizedContentType)) {
            return "HTML";
        }
        if ("text/csv".equals(normalizedContentType)
            || "application/csv".equals(normalizedContentType)
            || "application/vnd.ms-excel".equals(normalizedContentType)) {
            return "CSV";
        }

        String extension = getExtension(fileName);
        if (!extension.isBlank()) {
            return switch (extension) {
                case "pdf" -> "PDF";
                case "docx" -> "DOCX";
                case "pptx" -> "PPTX";
                case "xlsx" -> "XLSX";
                case "md", "markdown" -> "MD";
                case "html", "htm" -> "HTML";
                case "csv" -> "CSV";
                case "txt" -> "TXT";
                default -> "OTHER";
            };
        }

        if ("text/plain".equals(normalizedContentType)) {
            return "TEXT";
        }
        if (normalizedContentType == null || normalizedContentType.isBlank()) {
            return "OTHER";
        }
        return "OTHER";
    }

    private String documentTypeLabel(String type) {
        return switch (type) {
            case "PDF" -> "PDF";
            case "DOCX" -> "DOCX";
            case "PPTX" -> "PPTX";
            case "XLSX" -> "XLSX";
            case "TXT" -> "TXT";
            case "MD" -> "Markdown";
            case "HTML" -> "HTML";
            case "CSV" -> "CSV";
            case "TEXT" -> "文本录入";
            default -> "其他";
        };
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        int separator = contentType.indexOf(';');
        String normalized = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private String getExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).trim().toLowerCase(Locale.ROOT);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}



