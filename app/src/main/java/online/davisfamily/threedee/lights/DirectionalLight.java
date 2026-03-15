package online.davisfamily.threedee.lights;

import online.davisfamily.threedee.matrices.Vec3;

public class DirectionalLight {

    private final Vec3 direction = new Vec3();
    private float ambient = 0.55f;
    private float diffuseStrength = 0.45f;

    public DirectionalLight(Vec3 direction, float ambient, float diffuseStrength) {
        setDirection(direction);
        this.ambient = ambient;
        this.diffuseStrength = diffuseStrength;
    }

    public Vec3 getDirection() {
        return direction;
    }

    public void setDirection(Vec3 dir) {
        direction.set(dir).mutableNormalize();
    }

    public float getAmbient() {
        return ambient;
    }

    public void setAmbient(float ambient) {
        this.ambient = ambient;
    }

    public float getDiffuseStrength() {
        return diffuseStrength;
    }

    public void setDiffuseStrength(float diffuseStrength) {
        this.diffuseStrength = diffuseStrength;
    }

}
