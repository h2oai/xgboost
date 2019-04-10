package ai.h2o;

public class App {

    public static final String VERSION = "!SUBST_VERSION";
    public static final String BACKEND = "!SUBST_BACKEND";
    public static final String OS = "!SUBST_OS";

    public static void main(String[] args) {
        System.out.println(String.format("XGBoost Library Details:\n\tVersion: %s\n\tBackend: %s\n\tOS: %s", VERSION, BACKEND, OS));
    }
}
