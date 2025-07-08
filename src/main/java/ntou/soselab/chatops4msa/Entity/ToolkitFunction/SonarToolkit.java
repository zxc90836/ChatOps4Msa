package ntou.soselab.chatops4msa.Entity.ToolkitFunction;

import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class SonarToolkit {

    public String toolkitSonarProjectExtract(String project_name,String metric_response, String issue_response) throws JSONException {
        JSONObject metricJson = new JSONObject(metric_response);
        JSONObject issueJson = new JSONObject(issue_response);

        JSONObject cleaned = new JSONObject();
        JSONObject metrics = new JSONObject();
        JSONObject issues = new JSONObject();

        JSONArray measures = metricJson.optJSONObject("component")
                .optJSONArray("measures");
        for (int i = 0; i < measures.length(); i++) {
            JSONObject measure = measures.getJSONObject(i);
            String key = measure.optString("metric");
            String value = measure.optString("value");
            metrics.put(key, tryParseDouble(value));
        }

        JSONArray issuesList = issueJson.optJSONArray("issues");
        int smellCount = 0, bugCount = 0, vulnCount = 0;
        for (int i = 0; i < issuesList.length(); i++) {
            JSONObject issue = issuesList.getJSONObject(i);
            String type = issue.optString("type");
            switch (type) {
                case "CODE_SMELL": smellCount++; break;
                case "BUG": bugCount++; break;
                case "VULNERABILITY": vulnCount++; break;
            }
        }

        cleaned.put("project", project_name);
        issues.put("code_smell", smellCount);
        issues.put("bug", bugCount);
        issues.put("vulnerability", vulnCount);

        cleaned.put("metrics", metrics);
        cleaned.put("critical_issues", issues);

        return cleaned.toString(); // ✅ 回傳字串，讓 YAML 可進一步 join / assign
    }

    private double tryParseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return -1.0;
        }
    }
    // 封裝每個語言的品質指標參數，包括 code smells、complexity 等
    static class MetricConstants {
        double[] codeSmells;
        double[] complexity;
        double[] dupLines;
        double[] reliability;
        double[] coverage;
        double[] security;
        double[] issueFallback;

        MetricConstants(double[] codeSmells, double[] complexity, double[] dupLines, double[] reliability,
                        double[] coverage, double[] security, double[] issueFallback) {
            this.codeSmells = codeSmells;
            this.complexity = complexity;
            this.dupLines = dupLines;
            this.reliability = reliability;
            this.coverage = coverage;
            this.security = security;
            this.issueFallback = issueFallback;
        }
    }

    // 各語言對應的指標分布參數 (c, λ)
    private static final Map<String, MetricConstants> languageParams = new HashMap<>() {{
        put("java", new MetricConstants(
                new double[]{1.123, 50.731},
                new double[]{155.228, 50.947, 40.902},
                new double[]{0.439, 63.284},
                new double[]{1.0, 54.376},
                new double[]{0.8, 10.0},
                new double[]{1.0, 18.168},
                new double[]{0, 5.0}
        ));
        put("javascript", new MetricConstants(
                new double[]{0.036, 60.260},
                new double[]{166.692, 88.415, 78.289},
                new double[]{0.145, 163.258},
                new double[]{0.054, 63.313},
                new double[]{0.8, 10.0},
                new double[]{1.0, 18.168},
                new double[]{0, 5.0}
        ));
        put("python", new MetricConstants(
                new double[]{0.004, 37.177},
                new double[]{162.321, 53.497, 52.789},
                new double[]{0.081, 124.342},
                new double[]{0.004, 387.551},
                new double[]{0.8, 10.0},
                new double[]{1.0, 18.168},
                new double[]{0, 5.0}
        ));
        put("typescript", new MetricConstants(
                new double[]{0.017, 16.530},
                new double[]{127.273, 51.616, 66.733},
                new double[]{0.085, 102.796},
                new double[]{0.021, 18.168},
                new double[]{0.8, 10.0},
                new double[]{1.0, 18.168},
                new double[]{0, 5.0}
        ));
    }};

    // 主計算函式：輸入 Sonar JSON 與語言類型，回傳分數 JSON
    public String toolkitSonarScoreDistribution(String sonar_json, String language) throws JSONException {
        JSONObject input = new JSONObject(sonar_json);
        JSONObject metrics = input.optJSONObject("metrics");
        JSONObject issues = input.optJSONObject("critical_issues");

        if (metrics == null || issues == null) {
            return buildErrorResponse(input, "缺少 metrics 或 critical_issues 欄位");
        }

        // 根據語言取得對應參數（若無對應則使用 java）
        MetricConstants params = languageParams.getOrDefault(language.toLowerCase(), languageParams.get("java"));

        // 擷取 ncloc 作為正規化依據
        double ncloc = metrics.optDouble("ncloc", -1);

        // 取得 code smells 並進行正規化（除以 ncloc）
        double codeSmells = metrics.optDouble("code_smells", -1);
        double normalizedCodeSmells = (ncloc > 0 && codeSmells >= 0) ? codeSmells / ncloc : -1;

        // 對 bugs 進行正規化
        double bugs = metrics.optDouble("bugs", -1);
        double normalizedBugs = (ncloc > 0 && bugs >= 0) ? bugs / ncloc : -1;

        // 對 vulnerabilities 進行正規化
        double vulnerabilities = metrics.optDouble("vulnerabilities", -1);
        double normalizedVulnerabilities = (ncloc > 0 && vulnerabilities >= 0) ? vulnerabilities / ncloc : -1;

        double complexity = metrics.optDouble("complexity", -1);
        double dupDensity = metrics.optDouble("duplicated_lines_density", -1);

        // 避免 coverage=0 拿到滿分（視為缺值）
        double coverageValue = metrics.optDouble("coverage", -1);
        if (coverageValue == 0) coverageValue = -1;

        // 計算 Maintainability 維度
        double maintainability = avg(
                scoreMonotonic(normalizedCodeSmells, params.codeSmells[0], params.codeSmells[1]),
                scoreNonMonotonic(complexity, params.complexity[0], params.complexity[1]),
                scoreMonotonic(dupDensity, params.dupLines[0], params.dupLines[1])
        );

        // 計算 Reliability 維度（含 bugs 正規化）
        double reliability = avg(
                scoreMonotonic(metrics.optDouble("reliability_rating", -1), params.reliability[0], params.reliability[1]),
                scoreMonotonic(normalizedBugs, params.issueFallback[0], params.issueFallback[1])
        );

        // 計算 Functionality 維度（含 vulnerabilities 正規化）
        double functionality = avg(
                scoreMonotonic(coverageValue, params.coverage[0], params.coverage[1]),
                scoreMonotonic(metrics.optDouble("security_rating", -1), params.security[0], params.security[1]),
                scoreMonotonic(normalizedVulnerabilities, params.issueFallback[0], params.issueFallback[1])
        );

        double totalScore = avg(maintainability, reliability, functionality);

        // 封裝回傳 JSON 結果
        JSONObject result = new JSONObject();
        result.put("project", input.optString("project", "unknown"));
        result.put("score", round(totalScore));
        JSONObject dim = new JSONObject();
        dim.put("maintainability", round(maintainability));
        dim.put("reliability", round(reliability));
        dim.put("functionality", round(functionality));
        result.put("dimensions", dim);
        result.put("input", input);

        return result.toString();
    }

    // 單調型評分函式：x <= c 時得滿分，否則指數衰減
    private double scoreMonotonic(double x, double c, double lambda) {
        if (x < 0) return -1;
        return x <= c ? 100.0 : 100.0 * Math.exp(-lambda * (x - c));
    }

    // 非單調型評分函式：離理想值越遠，分數越低
    private double scoreNonMonotonic(double x, double ideal, double tolerance) {
        if (x < 0) return -1;
        double diff = Math.abs(x - ideal);
        double score = 100.0 - (diff / tolerance) * 100.0;
        return Math.max(score, 0);
    }

    // 平均函式（跳過 -1 無效值）
    private double avg(double... values) {
        double sum = 0;
        int count = 0;
        for (double v : values) {
            if (v >= 0) {
                sum += v;
                count++;
            }
        }
        return count > 0 ? sum / count : -1;
    }

    // 四捨五入至小數第一位
    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    // 錯誤訊息格式化處理
    private String buildErrorResponse(JSONObject input, String message) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("score", -1);
        result.put("dimensions", new JSONObject());
        result.put("input", input);
        result.put("error", message);
        return result.toString();
    }
}

