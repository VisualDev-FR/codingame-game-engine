package com.codingame.gameengine.module.entities;

public abstract class Entity<T extends Entity<?>> {
    final int id;
    EntityState state;

    private int x, y, zIndex;
    private double scaleX = 1, scaleY = 1;
    private boolean visible = true;
    private double rotation, alpha = 1;
    Group parent;

    static enum Type {
        CIRCLE, LINE, RECTANGLE, SPRITE, TEXT, GROUP
    }

    Entity() {
        id = ++EntityManager.ENTITY_COUNT;
        state = new EntityState();
    }

    @SuppressWarnings("unchecked")
    protected T self() {
        return (T) this;
    }

    int getId() {
        return id;
    }

    protected void set(String key, Object value) {
        state.put(key, value);
    }

    abstract Type getType();

    /**
     * Sets the X coordinate of this <code>Entity</code> in world units.
     * 
     * @param x
     *            the X coordinate for this <code>Entity</code>.
     * @return this <code>Entity</code>.
     */
    public T setX(int x) {
        this.x = x;
        set("x", x);
        return self();
    }

    /**
     * Sets the Y coordinate of this <code>Entity</code> in world units.
     * 
     * @param y
     *            the Y coordinate for this <code>Entity</code>.
     * @return this <code>Entity</code>.
     */
    public T setY(int y) {
        this.y = y;
        set("y", y);
        return self();
    }

    /**
     * Sets the z-index of this <code>Entity</code> used to compute the display order for overlapping entities.
     * <p>
     * An <code>Entity</code> with a higher z-index is displayed over one with a smaller z-index.
     * <p>
     * In case of equal values, the most recently created <code>Entity</code> will be on top.
     * 
     * @param zIndex
     *            the z-index for this <code>Entity</code>.
     * @return this <code>Entity</code>.
     */
    public T setZIndex(int zIndex) {
        this.zIndex = zIndex;
        set("zIndex", zIndex);
        return self();
    }

    /**
     * Sets the horizontal scale of this <code>Entity</code> as a percentage.
     * 
     * @param scaleX
     *            the horizontal scale for this <code>Entity</code>.
     * @return this <code>Entity</code>.
     */
    public T setScaleX(double scaleX) {
        this.scaleX = scaleX;
        set("scaleX", scaleX);
        return self();
    }

    /**
     * Sets the vertical scale of this <code>Entity</code> as a percentage.
     * 
     * @param scaleY
     *            the vertical scale for this <code>Entity</code>.
     * @return this <code>Entity</code>.
     */
    public T setScaleY(double scaleY) {
        this.scaleY = scaleY;
        set("scaleY", scaleY);
        return self();
    }

    /**
     * Sets the alpha of this <code>Entity</code> as a percentage.
     * <p>
     * 1 is opaque and 0 is invisible.
     * 
     * @param alpha
     *            the alpha for this <code>Entity</code>.
     * @exception IllegalArgumentException
     *                if alpha &lt; 0 or alpha &gt; 1
     * @return this <code>Entity</code>.
     */
    public T setAlpha(double alpha) {
        requireValidAlpha(alpha);

        this.alpha = alpha;
        set("alpha", alpha);
        return self();
    }

    /**
     * Sets both the horizontal and vertical scale of this <code>Entity</code> to the same percentage.
     * 
     * @param scale
     *            the scale for this <code>Entity</code>.
     * @return this <code>Entity</code>.
     */
    public T setScale(double scale) {
        setScaleX(scale);
        setScaleY(scale);
        return self();
    }

    /**
     * Sets the rotation of this <code>Entity</code> in radians.
     * 
     * @param rotation
     *            the rotation for this <code>Entity</code>.
     * @return this <code>Entity</code>.
     */
    public T setRotation(double rotation) {
        this.rotation = rotation;
        set("rotation", rotation);
        return self();
    }

    /**
     * Flags this <code>Entity</code> to be drawn on screen or not.
     * <p>
     * Default is true.
     * 
     * @param visible
     *            the value for this <code>Entity</code>'s visible flag.
     * @return this <code>Entity</code>.
     */
    public T setVisible(boolean visible) {
        this.visible = visible;
        set("visible", visible);
        return self();
    }

    /**
     * Returns the X coordinate of this <code>Entity</code> in world units.
     * 
     * @return the X coordinate of this <code>Entity</code>.
     */
    public int getX() {
        return x;
    }

    /**
     * Returns the Y coordinate of this <code>Entity</code> in world units.
     * 
     * @return the Y coordinate of this <code>Entity</code>.
     */
    public int getY() {
        return y;
    }

    /**
     * Returns the z-index of this <code>Entity</code> used to compute the display order for overlapping entities.
     * 
     * @return the z-index of this <code>Entity</code>.
     */
    public int getZIndex() {
        return zIndex;
    }

    /**
     * Returns the horizontal scale of this <code>Entity</code> as a percentage.
     * <p>
     * Default is 1.
     * 
     * @return the horizontal scale of this <code>Entity</code>.
     */
    public double getScaleX() {
        return scaleX;
    }

    /**
     * Returns the vertical scale of this <code>Entity</code> as a percentage.
     * <p>
     * Default is 1.
     * 
     * @return the vertical scale of this <code>Entity</code>.
     */
    public double getScaleY() {
        return scaleY;
    }

    /**
     * Returns the alpha of this <code>Entity</code> as a percentage.
     * <p>
     * Default is 1.
     * 
     * @return the alpha of this <code>Entity</code>.
     */
    public double getAlpha() {
        return alpha;
    }

    /**
     * Returns the rotation of this <code>Entity</code> in radians.
     * 
     * @return the rotation coordinate of this <code>Entity</code>.
     */
    public double getRotation() {
        return rotation;
    }

    /**
     * Returns whether this <code>Entity</code> is flagged to be drawn on screen.
     * 
     * @return the value of the visible flag of this <code>Entity</code>.
     */
    public boolean isVisible() {
        return visible;
    }

    protected static void requireValidAlpha(double alpha) {
        if (alpha > 1) {
            throw new IllegalArgumentException("An alpha may not exceed 1");
        } else if (alpha < 0) {
            throw new IllegalArgumentException("An alpha may not be less than 0");
        }
    }

    protected static void requireValidColor(int color) {
        if (color > 0xFFFFFF) {
            throw new IllegalArgumentException(color + "is not a valid RGB integer.");
        }
    }
}