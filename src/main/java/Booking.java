package main.java;

import java.util.Date;

/**
 * @author Brett Gavin
 */
public class Booking {
	private String name;
	private Date dateStart, dateEnd;
	
	/**
	 * @param name the name of the user who booked the room
	 * @param start the date the booking starts
	 * @param end the date the booking ends
	 */
	public Booking(String name, Date start, Date end) {
		this.name = name;
		this.dateStart = start;
		this.dateEnd = end;
	}
	
	/**
	 * @return the name of the user who booked the room
	 */
	public String getName() {
		return this.name;
	}
	
	/**
	 * @return the date the booking starts
	 */
	public Date getStart() {
		return this.dateStart;
	}
	
	/**
	 * @return the date the booking ends
	 */
	public Date getEnd() {
		return this.dateEnd;
	}
}