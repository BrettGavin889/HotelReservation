package main.java;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import io.vertx.core.json.JsonObject;

public class Room {
	//enum containing the types of beds
	public enum Bed {
		TWIN, FULL, QUEEN, KING;
	}
	
	//variables containing information about the room
	protected List<Bed> beds;
	protected double price;
	protected int floor;
	protected boolean isCorner;
	protected boolean isWindow;
	private List<Booking> bookings;
	protected int roomNumber;
	protected int maxOccupants;
	
	/**
	 * @param roomNum the number of the room
	 */
	public Room(int roomNum) {
		bookings = new LinkedList<Booking>();
		beds = new LinkedList<Bed>();
		this.roomNumber = roomNum;
		this.floor = roomNum / 100;
	}
	
	/**
	 * @param start the start date
	 * @param end the end date
	 * @return whether or not the room is available for the date range
	 */
	public boolean isAvailable(Date start, Date end) {
		//compare the date range to each booking for the room
		for(Booking booking: bookings) {
			// @TODO - check that the booking and start/end dates aren't null 
			// if the range overlaps with the current booking, the room is not available
			if((booking.getStart().compareTo(end) <= 0) && (booking.getEnd().compareTo(start) >= 0)) {
				return false;
			}
		}
		
		//if no conflicts, the room is available
		return true;
	}
	
	/**
	 * @param start the start date
	 * @param end the end date
	 * @param name the name of the user booking the room
	 * @return whether or not the booking was successful
	 */
	public boolean bookRoom(Date start, Date end, String name) {
		//if the room is available, book the room
		if(isAvailable(start, end)) {
			bookings.add(new Booking(name, start, end));
			return true;
		}
		
		//otherwise we cannot book the room (ie its already taken)
		return false;
	}
	
	/**
	 * @return the room and all its info as a JsonObject
	 */
	public JsonObject getRoom() {
		String beds = "" + this.beds.size() +": ";
		for(Bed bed: this.beds) {
			beds+= bed.name() + ", ";
		}
		
		return new JsonObject()
        .put("roomNumber", this.roomNumber)
        .put("beds", beds)
        .put("floor", this.floor)
        .put("isCorner", this.isCorner)
        .put("isWindow", this.isWindow)
        .put("isPenthouse", this.isPenthouse())
        .put("price", this.price)
        .put("occupants", this.maxOccupants);
	}
	
	/********************************  Start of generic getter methods ***********************/
	/**
	 * @return the number of beds in the room
	 */
	public int getNumBeds() {
		return this.beds.size();
	}
	
	/**
	 * @return whether or not the room has window(s)
	 */
	public boolean isWindow() {
		return this.isWindow;
	}
	
	/**
	 * @return whether or not the room is a corner room
	 */
	public boolean isCorner() {
		return this.isCorner;
	}
	
	/**
	 * @return the floor the room is on
	 */
	public int getFloor() {
		return this.floor;
	}
	
	/**
	 * @return the list of bookings for the room
	 */
	public List<Booking> getBookings() {
		return this.bookings;
	}

	/** 
	 * @return the maximum number of occupants for the room
	 */
	public int getOccupants() {
		return this.maxOccupants;
	}
	
	/**
	 * @return the price of the room
	 */
	public double getPrice() {
		return this.price;
	}
	
	/**
	 * @return whether or not the room is a penthouse
	 */
	public boolean isPenthouse(){
		return (this.beds.size() > 2);
	}
}
