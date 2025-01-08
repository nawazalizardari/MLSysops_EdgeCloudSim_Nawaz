package edu.boun.edgecloudsim.utils;

public class Coordinates {

	private double X;
	private double Y;
	private Boolean isDead;
	private Integer id;

	private double time;

	private double energyConsumed;

	public double getX() {
		return X;
	}

	public double getY() {
		return Y;
	}

	public void setX(double x) {
		X = x;
	}

	public void setY(double y) {
		Y = y;
	}

	public Boolean isDead() {
		return isDead;
	}

	public Coordinates(double x, double y, boolean isDead, int id, Double time, Double energyConsumed) {
		this.X = x;
		this.Y = y;
		this.isDead = isDead;
		this.id = id;
		this.time = time;
		this.energyConsumed = energyConsumed;
		if (isDead) {
			this.energyConsumed = -1.0;
		}
	}

	@Override
	public String toString() {
		return "Coordinates{" + "X=" + X + ", Y=" + Y + ", isDead=" + isDead + ", id=" + id + ", time='" + time + '\''
				+ '}';
	}

	public Boolean getDead() {
		return isDead;
	}

	public void setDead(Boolean dead) {
		isDead = dead;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Double getTime() {
		return time;
	}

	public void setTime(Double time) {
		this.time = time;
	}

	public Double getEnergyConsumed() {
		return energyConsumed;
	}

	public void setEnergyConsumed(Double energyConsumed) {
		this.energyConsumed = energyConsumed;
	}
}
