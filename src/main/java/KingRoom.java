package main.java;

/**
 * @author Brett Gavin
 */
public class KingRoom extends Room {
	/**
	 * Create a room without a roomNumber
	 * Note: this really shouldn't be used
	 */
	public KingRoom() {
		super(0);
		init(false);
	}
	
	/**
	 * @param roomNumber the number of the room
	 * @param isDouble whether or not there are 2 beds in the room
	 */
	public KingRoom (int roomNumber, boolean isDouble) {
		super(roomNumber);
		init(isDouble);
	}
	
	/**
	 * @param isDouble whether or not there are 2 beds in the room
	 */
	private void init(boolean isDouble) {
		//set up the specifics of the room
		super.price = 500;
		super.beds.add(Bed.KING);
		if(isDouble)
			super.beds.add(Bed.KING);
		super.isCorner = false;
		super.maxOccupants = 2 * super.beds.size() + 1;
	}
}