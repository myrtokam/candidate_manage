import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class Main {

    static final String FILE_PATH = "DATA/career_day_candidates.csv";

    public static void main(String[] args) {
        List<Map<String, String>> rows = readCsvAsMaps(FILE_PATH);

        if (rows.isEmpty()) {
            System.out.println("Το CSV δεν έχει δεδομένα (μόνο header ή είναι άδειο).");
            return;
        }

        int total = rows.size();
        System.out.println("Σύνολο υποψηφίων: " + total);
        System.out.println("--------------------------------------------------");


        printSeniorityStats(rows);
        printTopRoles(rows, 5);
        printDistribution(rows, "primary_skill", "Skill distribution (primary_skill)");
        printDistribution(rows, "overall_impression", "Ποιότητα (overall_impression)");
        printDistribution(rows, "follow_up_priority", "Follow-up workload (follow_up_priority)");
        printConversionFunnel(rows);
        printCvDropRate(rows);
        printDistribution(rows, "availability", "Availability insight (availability)");
        printRemoteMismatch(rows);
        printEventSummary(rows);
    }


    private static List<Map<String, String>> readCsvAsMaps(String path) {
        List<Map<String, String>> rows = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(path))) {

            String headerLine = br.readLine();
            if (headerLine == null) return rows;

            List<String> headers = parseCsvLine(headerLine);

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                List<String> values = parseCsvLine(line);

                // αν μια γραμμή έχει λιγότερα πεδία, συμπληρώνω κενά
                while (values.size() < headers.size()) values.add("");

                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), values.get(i).trim());
                }
                rows.add(row);
            }

        } catch (IOException e) {
            System.out.println("Σφάλμα ανάγνωσης CSV: " + e.getMessage());
        }

        return rows;
    }


    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        out.add(current.toString());
        return out;
    }

    private static void printSeniorityStats(List<Map<String, String>> rows) {
        int junior = 0;
        int mid = 0;
        int senior = 0;

        for (Map<String, String> r : rows) {
            String ageGroup = get(r, "age_group"); // π.χ. 20+, 30+, 40+, 50+
            int years = parseIntSafe(get(r, "years_of_experience"));

            String bucket = getSeniorityBucket(ageGroup, years);
            if (bucket.equals("junior")) junior++;
            else if (bucket.equals("mid")) mid++;
            else senior++;
        }

        int total = rows.size();
        System.out.println("1) Seniority (από age_group + years_of_experience)");
        System.out.printf("   Junior: %d (%.1f%%)%n", junior, percent(junior, total));
        System.out.printf("   Mid:    %d (%.1f%%)%n", mid, percent(mid, total));
        System.out.printf("   Senior: %d (%.1f%%)%n", senior, percent(senior, total));
        System.out.println("--------------------------------------------------");
    }


    private static String getSeniorityBucket(String ageGroup, int years) {
        if (years <= 2) return "junior";
        if (years <= 5) return "mid";
        return "senior";
    }

    private static void printTopRoles(List<Map<String, String>> rows, int topN) {
        Map<String, Integer> counts = new HashMap<>();

        for (Map<String, String> r : rows) {
            addCountIfNotEmpty(counts, get(r, "interested_role_1"));
            addCountIfNotEmpty(counts, get(r, "interested_role_2"));
            addCountIfNotEmpty(counts, get(r, "interested_role_3"));
        }

        List<Map.Entry<String, Integer>> sorted = sortByValueDesc(counts);

        System.out.println("2) Top " + topN + " ρόλοι ενδιαφέροντος (interested_role_1/2/3)");
        for (int i = 0; i < Math.min(topN, sorted.size()); i++) {
            System.out.println("   " + (i + 1) + ". " + sorted.get(i).getKey() + " – " + sorted.get(i).getValue());
        }
        System.out.println("--------------------------------------------------");
    }

    private static void printDistribution(List<Map<String, String>> rows, String column, String title) {
        Map<String, Integer> counts = new HashMap<>();

        for (Map<String, String> r : rows) {
            String v = get(r, column);
            if (v.isEmpty()) v = "(empty)";
            counts.put(v, counts.getOrDefault(v, 0) + 1);
        }

        List<Map.Entry<String, Integer>> sorted = sortByValueDesc(counts);

        System.out.println(title);
        for (Map.Entry<String, Integer> e : sorted) {
            System.out.printf("   %s: %d (%.1f%%)%n", e.getKey(), e.getValue(), percent(e.getValue(), rows.size()));
        }
        System.out.println("--------------------------------------------------");
    }
    private static void printConversionFunnel(List<Map<String, String>> rows) {
        int total = rows.size();

        int contacted = 0;
        int calls = 0;
        int interviews = 0;

        for (Map<String, String> r : rows) {
            String status = get(r, "follow_up_status");

            if (!status.isEmpty() && !status.equalsIgnoreCase("not_contacted")) {
                contacted++;
            }
            if (status.equalsIgnoreCase("called") || status.equalsIgnoreCase("interview_scheduled")) {
                calls++;
            }
            if (status.equalsIgnoreCase("interview_scheduled")) {
                interviews++;
            }
        }

        System.out.println("6) Conversion funnel (follow_up_status)");
        System.out.println("   Candidates: " + total);
        System.out.println("   Contacted:  " + contacted);
        System.out.println("   Calls:      " + calls);
        System.out.println("   Interviews: " + interviews);
        System.out.println("--------------------------------------------------");
    }


    private static void printCvDropRate(List<Map<String, String>> rows) {
        int yes = 0;
        for (Map<String, String> r : rows) {
            String left = get(r, "left_cv");
            if (left.equalsIgnoreCase("yes")) yes++;
        }
        System.out.println("7) CV drop rate (left_cv)");
        System.out.printf("   %d/%d άφησαν CV → %.1f%%%n", yes, rows.size(), percent(yes, rows.size()));
        System.out.println("--------------------------------------------------");
    }

    private static void printRemoteMismatch(List<Map<String, String>> rows) {
        int remoteOnly = 0;
        int notRemoteOnly = 0;

        for (Map<String, String> r : rows) {
            String pref = get(r, "work_preference");
            if (pref.equalsIgnoreCase("remote")) remoteOnly++;
            else notRemoteOnly++;
        }

        System.out.println("9) Remote / onsite mismatch (proxy από work_preference)");
        System.out.printf("   Remote-only: %d (%.1f%%)%n", remoteOnly, percent(remoteOnly, rows.size()));
        System.out.printf("   Not remote-only: %d (%.1f%%)%n", notRemoteOnly, percent(notRemoteOnly, rows.size()));
        System.out.println("--------------------------------------------------");
    }


    private static void printEventSummary(List<Map<String, String>> rows) {
        int total = rows.size();


        int junior = 0;
        int mid = 0;
        int senior = 0;


        Map<String, Integer> skillCounts = new HashMap<>();


        int highPriority = 0;
        int interviews = 0;

        for (Map<String, String> r : rows) {
            int years = parseIntSafe(get(r, "years_of_experience"));
            String ageGroup = get(r, "age_group");
            String bucket = getSeniorityBucket(ageGroup, years);
            if (bucket.equals("junior")) junior++;
            else if (bucket.equals("mid")) mid++;
            else senior++;

            addCountIfNotEmpty(skillCounts, get(r, "primary_skill"));

            if (get(r, "follow_up_priority").equalsIgnoreCase("high")) highPriority++;
            if (get(r, "follow_up_status").equalsIgnoreCase("interview_scheduled")) interviews++;
        }

        List<Map.Entry<String, Integer>> topSkills = sortByValueDesc(skillCounts);
        String skill1 = topSkills.size() > 0 ? topSkills.get(0).getKey() : "(none)";
        String skill2 = topSkills.size() > 1 ? topSkills.get(1).getKey() : "(none)";

        System.out.println("10) Event summary");
        System.out.println("   Στο event καταχωρήθηκαν " + total + " υποψήφιοι.");
        System.out.printf("   Seniority: %.1f%% junior, %.1f%% mid, %.1f%% senior.%n",
                percent(junior, total), percent(mid, total), percent(senior, total));
        System.out.println("   Κύρια skills: " + skill1 + " και " + skill2 + ".");
        System.out.println("   High priority: " + highPriority + " άτομα, Interviews: " + interviews + ".");
        System.out.println("--------------------------------------------------");
    }


    private static void addCountIfNotEmpty(Map<String, Integer> map, String value) {
        if (value == null) return;
        String v = value.trim();
        if (v.isEmpty()) return;
        map.put(v, map.getOrDefault(v, 0) + 1);
    }

    private static List<Map.Entry<String, Integer>> sortByValueDesc(Map<String, Integer> map) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(map.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return list;
    }

    private static String get(Map<String, String> row, String key) {
        String v = row.get(key);
        return v == null ? "" : v.trim();
    }

    private static int parseIntSafe(String s) {
        try {
            if (s == null || s.trim().isEmpty()) return 0;
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static double percent(int part, int total) {
        if (total == 0) return 0.0;
        return (part * 100.0) / total;
    }
}
