package com.simulation.airdefense;

/**
 * {@link App}'i başlatan düz main sınıfı.
 * <p>
 * Modül olmayan (classpath) bir projede {@code Application} alt sınıfını doğrudan main
 * olarak çalıştırmak "JavaFX runtime components are missing" hatasına yol açar; bu
 * sınıf aracılığıyla başlatmak hem {@code mvn javafx:run} hem de jar ile çalıştırmayı mümkün kılar.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        App.main(args);
    }
}
