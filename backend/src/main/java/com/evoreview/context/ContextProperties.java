package com.evoreview.context;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "evoreview.context")
public class ContextProperties {

    private int modelInputBudget = 12000;
    private int promptOverhead = 2000;
    private int safetyMargin = 1000;
    private int maxRelatedFilesPerSlice = 30;
    private long maxFileKb = 256;
    private long maxSnapshotMb = 50;
    private String storeDir = ".local/contexts";
    private String snapshotCacheDir = ".local/snapshots";
    private String policy = "heuristic-v1";
    private String tokenEstimator = "chars-div-4-v1";
    private List<String> sensitivePatterns = new ArrayList<>(List.of(
            ".env*", "*.pem", "*.key", "*.p12", "*.jks", "credentials*", "secrets*"));
    private Recall recall = new Recall();
    private CoChange coChange = new CoChange();

    public int contextBudget() {
        return modelInputBudget - promptOverhead - safetyMargin;
    }

    public Path resolvedStoreDir() {
        return resolveAgainstWorkingDir(storeDir);
    }

    public Path resolvedSnapshotCacheDir() {
        return resolveAgainstWorkingDir(snapshotCacheDir);
    }

    private static Path resolveAgainstWorkingDir(String path) {
        Path p = Path.of(path);
        return p.isAbsolute() ? p : Path.of(System.getProperty("user.dir")).resolve(p);
    }

    public int getModelInputBudget() {
        return modelInputBudget;
    }

    public void setModelInputBudget(int modelInputBudget) {
        this.modelInputBudget = modelInputBudget;
    }

    public int getPromptOverhead() {
        return promptOverhead;
    }

    public void setPromptOverhead(int promptOverhead) {
        this.promptOverhead = promptOverhead;
    }

    public int getSafetyMargin() {
        return safetyMargin;
    }

    public void setSafetyMargin(int safetyMargin) {
        this.safetyMargin = safetyMargin;
    }

    public int getMaxRelatedFilesPerSlice() {
        return maxRelatedFilesPerSlice;
    }

    public void setMaxRelatedFilesPerSlice(int maxRelatedFilesPerSlice) {
        this.maxRelatedFilesPerSlice = maxRelatedFilesPerSlice;
    }

    public long getMaxFileKb() {
        return maxFileKb;
    }

    public void setMaxFileKb(long maxFileKb) {
        this.maxFileKb = maxFileKb;
    }

    public long getMaxSnapshotMb() {
        return maxSnapshotMb;
    }

    public void setMaxSnapshotMb(long maxSnapshotMb) {
        this.maxSnapshotMb = maxSnapshotMb;
    }

    public String getStoreDir() {
        return storeDir;
    }

    public void setStoreDir(String storeDir) {
        this.storeDir = storeDir;
    }

    public String getSnapshotCacheDir() {
        return snapshotCacheDir;
    }

    public void setSnapshotCacheDir(String snapshotCacheDir) {
        this.snapshotCacheDir = snapshotCacheDir;
    }

    public String getPolicy() {
        return policy;
    }

    public void setPolicy(String policy) {
        this.policy = policy;
    }

    public String getTokenEstimator() {
        return tokenEstimator;
    }

    public void setTokenEstimator(String tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    public List<String> getSensitivePatterns() {
        return sensitivePatterns;
    }

    public void setSensitivePatterns(List<String> sensitivePatterns) {
        this.sensitivePatterns = sensitivePatterns;
    }

    public Recall getRecall() {
        return recall;
    }

    public void setRecall(Recall recall) {
        this.recall = recall;
    }

    public CoChange getCoChange() {
        return coChange;
    }

    public void setCoChange(CoChange coChange) {
        this.coChange = coChange;
    }

    public static class Recall {
        private boolean symbolGraph = true;
        private boolean conventions = true;
        private boolean bm25 = false;
        private boolean coChange = false;

        public boolean isSymbolGraph() {
            return symbolGraph;
        }

        public void setSymbolGraph(boolean symbolGraph) {
            this.symbolGraph = symbolGraph;
        }

        public boolean isConventions() {
            return conventions;
        }

        public void setConventions(boolean conventions) {
            this.conventions = conventions;
        }

        public boolean isBm25() {
            return bm25;
        }

        public void setBm25(boolean bm25) {
            this.bm25 = bm25;
        }

        public boolean isCoChange() {
            return coChange;
        }

        public void setCoChange(boolean coChange) {
            this.coChange = coChange;
        }
    }

    public static class CoChange {
        private int minHistoryCommits = 200;
        private int refreshIntervalDays = 7;

        public int getMinHistoryCommits() {
            return minHistoryCommits;
        }

        public void setMinHistoryCommits(int minHistoryCommits) {
            this.minHistoryCommits = minHistoryCommits;
        }

        public int getRefreshIntervalDays() {
            return refreshIntervalDays;
        }

        public void setRefreshIntervalDays(int refreshIntervalDays) {
            this.refreshIntervalDays = refreshIntervalDays;
        }
    }
}
