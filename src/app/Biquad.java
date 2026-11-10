package app;

/** RBJ Audio EQ Cookbook — peaking filter. */
public final class Biquad {
    private double b0, b1, b2, a1, a2;
    private double x1, x2, y1, y2;

    /** fc — center Hz, q — quality, gainDb — peak gain, fs — sample rate */
    public void setPeaking(double fc, double q, double gainDb, double fs) {
        double A = Math.pow(10.0, gainDb / 40.0);
        double w0 = 2 * Math.PI * fc / fs;
        double alpha = Math.sin(w0) / (2 * q);
        double cosw = Math.cos(w0);
        double b0 = 1 + alpha * A, b1 = -2 * cosw, b2 = 1 - alpha * A;
        double a0 = 1 + alpha / A, a1b = -2 * cosw, a2b = 1 - alpha / A;
        this.b0 = b0 / a0; this.b1 = b1 / a0; this.b2 = b2 / a0;
        this.a1 = a1b / a0; this.a2 = a2b / a0;
    }

    public double process(double x) {
        double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
        x2 = x1; x1 = x; y2 = y1; y1 = y;
        return y;
    }
}
