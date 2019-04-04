package ai.h2o;

public class App {

    public static final String VERSION = "0.83-SNAPSHOT";
    public static final String BACKEND = "minimal";
    public static final String OS = "osx";

    public static void main(String[] args) {
        System.out.println(String.format("XGBoost Library Details:\n\tVersion: %s\n\tBackend: %s\n\tOS: %s", VERSION, BACKEND, OS));
    }
}
