package main.java;

/**
 * @author Brett Gavin
 */
public class TwinRoom extends Room {
	/**
	 * Create a room without a roomNumber
	 * Note: this really shouldn't be used
	 */
	public TwinRoom() {
		super(0);
		init(false);
	}
	
	/**
	 * @param roomNumber the number of the room
	 * @param isDouble whether or not there are 2 beds in the room
	 */
	public TwinRoom (int roomNumber, boolean isDouble) {
		super(roomNumber);
		init(isDouble);
	}
	
	/**
	 * @param isDouble whether or not there are 2 beds in the room
	 */
	private void init(boolean isDouble) {
		//set up the specifics of the room
		super.price = 250;
		super.beds.add(Bed.TWIN);
		if(isDouble)
			super.beds.add(Bed.TWIN);
		super.isCorner = false;
		super.maxOccupants = 1 * super.beds.size();
	}
}
