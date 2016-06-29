package main.java;

/**
 * @author Brett Gavin
 */
public class FullRoom extends Room {
	/**
	 * Create a room without a roomNumber
	 * Note: this really shouldn't be used
	 */
	public FullRoom() {
		super(0);
		init(false);
	}
	
	/**
	 * @param roomNumber the number of the room
	 * @param isDouble whether or not there are 2 beds in the room
	 */
	public FullRoom (int roomNumber, boolean isDouble) {
		super(roomNumber);
		init(isDouble);
	}
	
	/**
	 * @param isDouble whether or not there are 2 beds in the room
	 */
	private void init(boolean isDouble) {
		//set up the specifics of the room
		super.price = 300;
		super.beds.add(Bed.FULL);
		if(isDouble)
			super.beds.add(Bed.FULL);
		super.isCorner = false;
		super.maxOccupants = super.beds.size() + 1;
	}
}