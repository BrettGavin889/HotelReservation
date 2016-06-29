package main.java;

/**
 * @author Brett Gavin
 */
public class PenthouseRoom extends Room {
	/**
	 * Create the room without a room number
	 * Note: this probably shouldn't be used
	 */
	public PenthouseRoom() {
		super(0);
		init();
	}
	
	/**
	 * @param roomNumber the number of the room
	 */
	public PenthouseRoom (int roomNumber) {
		super(roomNumber);
		init();
	}
	
	/**
	 * Set up the specifics of the room
	 */
	private void init() {
		super.price = 1000;
		super.beds.add(Bed.KING);
		super.beds.add(Bed.KING);
		super.beds.add(Bed.KING);
		super.isCorner = false;
		super.maxOccupants = 10;
	}
}
