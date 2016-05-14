package swarmBots.Rover16Utilities;

public class CommObject {
	int id;
	int x;
	int y;
	String terrain;
	String science;
	boolean stillExists = true;

	public CommObject() {
		super();
	}

	public CommObject(int id, int x, int y, String terrain, String science, boolean stillExists) {
		super();
		this.id = id;
		this.x = x;
		this.y = y;
		this.terrain = terrain;
		this.science = science;
		this.stillExists = stillExists;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getX() {
		return x;
	}

	public void setX(int x) {
		this.x = x;
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		this.y = y;
	}

	public String getTerrain() {
		return terrain;
	}

	public void setTerrain(String terrain) {
		this.terrain = terrain;
	}

	public String getScience() {
		return science;
	}

	public void setScience(String science) {
		this.science = science;
	}

	public boolean isStillExists() {
		return stillExists;
	}

	public void setStillExists(boolean stillExists) {
		this.stillExists = stillExists;
	}
}
