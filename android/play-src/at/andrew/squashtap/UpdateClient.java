package at.andrew.squashtap;

import android.content.Context;
import java.io.File;

/** Play builds contain no APK download/installation implementation. */
public final class UpdateClient {
    public interface Progress { void update(int percent); }
    public UpdateClient(Context context) { }
    public UpdatePolicy check() { throw disabled(); }
    public File download(UpdatePolicy release,Progress progress) { throw disabled(); }
    public void install(File apk) { throw disabled(); }
    private UnsupportedOperationException disabled() {
        return new UnsupportedOperationException("Updates are managed by Google Play.");
    }
}
