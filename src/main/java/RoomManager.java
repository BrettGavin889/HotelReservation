package main.java;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

import io.vertx.core.json.JsonObject;
//import io.vertx.example.web.angularjs.Room.Bed;

/**
 * @author Brett Gavin
 */
public class RoomManager {
	private List<Room> rooms;
	int numFloors, numRooms, percentWindows;
	
	/**
	 * create a generic room manager
	 */
	public RoomManager() {
		init(2,20, 100, true);
	}
	
	/**
	 * Create a room manager specifying aspects of the hotel
	 * 
	 * @param numFloors the number of floors in the hotel
	 * @param numRooms the number of rooms per floor
	 * @param percentWindows the percent of rooms that have windows
	 * @param isPenthouse whether or not the top floor is a penthouse
	 */
	public RoomManager(int numFloors, int numRooms, int percentWindows, boolean isPenthouse) {
		init(numFloors, numRooms, percentWindows, isPenthouse);
	}
	
	/**
	 * Initialize the room manager
	 */
	private void init(int numFloors, int numRooms, int percentWindows, boolean isPenthouse) {
		//TODO this whole method is very hacky
		this.numFloors = numFloors;
		this.numRooms = numRooms;
		this.percentWindows = percentWindows;
		rooms = new LinkedList<Room>();
		
		//add the penthouse if applicable
		if(isPenthouse && numFloors > 1) {
			rooms.add(new PenthouseRoom(numFloors * 100 + 1));
			numFloors--;
		}
			
		//for now assume all floors (except penthouse which is top) are the same
		for(int i=1;i<=numFloors;i++) {
			for(int j=1;j<=numRooms;j+=2) {
				Room room, room2;
				if(j<=numRooms / 4) {
					room = new KingRoom(i*100+j, false);
					room2 = new KingRoom(i*100+j+1, true);
				} else if(j<= (2 * numRooms / 4)) {
					room = new QueenRoom(i*100+j, false);
					room2 = new QueenRoom(i*100+j+1, true);
				} else if(j<= (3 * numRooms / 4)) {
					room = new FullRoom(i*100+j, false);
					room2 = new FullRoom(i*100+j+1, true);
				} else {
					room = new TwinRoom(i*100+j, false);
					room2 = new TwinRoom(i*100+j+1, true);
				}
				
				//roughly 50 percent have windows
				Random r = new Random();
				if(r.nextFloat() <= .50f)
					room.isWindow = true;
				if(r.nextFloat() <= .50f)
					room2.isWindow = true;
				rooms.add(room);
				rooms.add(room2);
			}
			
			//add 4 extra corner rooms (no windows)
			for(int k = 1; k<=4;k++) {
				Room room = new TwinRoom(i*100 + numRooms + k, false);
				room.isCorner = true;
				rooms.add(room);
			}
		}
	}
	
	/**
	 * @param name the name of the person booking the room
	 * @param start	the starting date
	 * @param end the ending date
	 * @param paymentInfo how the user is paying
	 * @param roomNum the room number
	 * @return whether or not the booking was successful
	 */
	public boolean bookRoom(String name, Date start, Date end, String paymentInfo, int roomNum) {	
		//find the room based on the number and try to book it
		for(Room room : this.rooms) {
			if(room.roomNumber == roomNum) {
				return room.bookRoom(start, end, name); 
			}
		}
		
		//couldn't find room
		return false;
	}
	
	/**
	 * @param rooms a list of rooms to check
	 * @param tester search criteria for how to decide if a room is valid
	 * @return a list of valid rooms based on the search criteria
	 */
	private List<Room> checkRooms(
		List<Room> rooms, Predicate<Room> tester) {
		LinkedList<Room> validRooms = new LinkedList<>();
		for(Room r : rooms) {
			if(tester.test(r)) {
				validRooms.add(r);
			}
		}
		return validRooms;
	}
	
	/**
	 * @param start the start date
	 * @param end the end date
	 * @param numBeds the number of beds
	 * @param isCorner whether or not the room is a corner
	 * @param isWindow whether or not the room has window(s)
	 * @param isPenthouse whether or not the room is a penthouse
	 * @param numOccupants the number of occupants the user will have
	 * @param maxPrice the maximum price the user will pay
	 * @param minPrice the minimum price the user will pay
	 * @return a list of matching rooms
	 */
	public List<JsonObject> getAvailableRooms(Date start, Date end, Integer numBeds, Boolean isCorner, Boolean isWindow, Boolean isPenthouse, Integer numOccupants, Integer maxPrice, Integer minPrice) {
		List<JsonObject> rooms = new LinkedList<>();
		
		List<Room> availableRooms = checkRooms(
				this.rooms,
				r -> r.isAvailable(start, end)
					&& (isWindow == null ? true : r.isWindow() == isWindow.booleanValue())
					&& (isCorner == null ? true : r.isCorner() == isCorner.booleanValue())
					&& (isPenthouse == null ? true : r.isPenthouse() == isPenthouse.booleanValue())
					&& (minPrice == null ? true : r.getPrice() >= minPrice.intValue())
					&& (maxPrice == null ? true : r.getPrice() <= maxPrice.intValue())
					&& (numBeds == null ? true : r.getNumBeds() == numBeds.intValue())
					&& (numOccupants == null ? true : r.getOccupants() >= numOccupants.intValue())
				);
		
		for(Room room : availableRooms) {
			rooms.add(room.getRoom());
		}
		return rooms;
	}
	
	/**
	 * @return a list of all rooms in the hotel
	 */
	public List<JsonObject> getAllRooms() {
		List<JsonObject> rooms = new LinkedList<>();
		for(Room room : this.rooms) {
			rooms.add(room.getRoom());
		}
		return rooms;
	}
	
	/**
	 * @param roomNumber the number of the room to get the bookings for
	 * @return a list of the bookings for the room
	 */
	public List<JsonObject> getBookings(int roomNumber) {
		//first find the room
		for(Room room : this.rooms) {
			if(room.roomNumber == roomNumber) {
				List<JsonObject> bookings = new LinkedList<>();
				for(Booking booking: room.getBookings()) {
					//add a jsonobject for each booking
					bookings.add(new JsonObject()
							.put("roomNumber", roomNumber)
							.put("start", booking.getStart().toString())
							.put("end", booking.getEnd().toString()));
				}
			
				return bookings;
			}
		}
		
		//default return in case the room number is invalid
		return null;
	}
	
	/**
	 * @param roomNumber the number of the room to get the price of
	 * @return the price of the room
	 */
	public double getPrice(int roomNumber) {
		for(Room room : this.rooms) {
			if(room.roomNumber == roomNumber) {
				return room.price;
			}
		}
		
		//default return in case the room number is invalid
		return -1;
	}
}
