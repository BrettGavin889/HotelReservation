package main.java;

/**
 * @author Brett Gavin
 */
public class QueenRoom extends Room {
	/**
	 * Create a room without a roomNumber
	 * Note: this really shouldn't be used
	 */
	public QueenRoom() {
		super(0);
		init(false);
	}
	
	/**
	 * @param roomNumber the number of the room
	 * @param isDouble whether or not there are 2 beds in the room
	 */
	public QueenRoom (int roomNumber, boolean isDouble) {
		super(roomNumber);
		init(isDouble);
	}
	
	/**
	 * @param isDouble whether or not there are 2 beds in the room
	 */
	private void init(boolean isDouble) {
		//set up the specifics of the room
		super.price = 400;
		super.beds.add(Bed.QUEEN);
		if(isDouble)
			super.beds.add(Bed.QUEEN);
		super.isCorner = false;
		super.maxOccupants = 2 * super.beds.size();
	}
}