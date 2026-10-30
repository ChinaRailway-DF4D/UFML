package io.github.ufml.launcher.minecraft;

@FunctionalInterface
public interface ProgressListener {
    void update(ProgressUpdate update);

    default void stage(String stage, String detail, double progress) {
        update(new ProgressUpdate(stage, detail, progress));
    }

    record ProgressUpdate(String stage, String detail, double progress) {
        public static ProgressUpdate indeterminate(String stage, String detail) {
            return new ProgressUpdate(stage, detail, -1);
        }
    }
}
