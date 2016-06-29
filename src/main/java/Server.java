package main.java;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
//import io.vertx.example.util.Runner;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.jdbc.JDBCAuth;
import io.vertx.ext.auth.jdbc.impl.JDBCAuthImpl;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.mail.LoginOption;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import io.vertx.ext.mail.StartTLSOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.FormLoginHandler;
import io.vertx.ext.web.handler.RedirectAuthHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.UserSessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * @author Brett Gavin
 * 
 *         While I did write most of this code, it is based on the vert.x
 *         examples, which can be found here:
 *         https://github.com/vert-x3/vertx-examples
 */
public class Server extends AbstractVerticle {
	// Convenience method so you can run it in your IDE
	public static void main(String[] args) {
		Runner.runExample(Server.class);
	}

	//connection to the mongo database containing room booking and user information
	private MongoClient mongo;
	private Connection conn;
	//RoomManager to keep track of which rooms are available
	private RoomManager manager;
	//variable to keep track of the logged on user
	private String currentUser="";
	//variables to store the users selections 
	private String start, end, isWindow, isPenthouse, isCorner;
	private Date startDate = null, endDate = null;
	private Integer beds, max, min, occupants;

	@Override
	public void start() throws Exception {
		// create a new instance of RoomManager to handle the rooms
		manager = new RoomManager();

		// Create a mongo client using all defaults (connect to localhost and
		// default port) using the database name "demo".
		mongo = MongoClient.createShared(vertx, new JsonObject().put("db_name", "demo"));

		// quick load of test data, this is a *sync* helper not intended for
		// real deployments
		// TODO switch this to a local mysql database
		setUpInitialData("jdbc:hsqldb:mem:test?shutdown=true");

		// Create a JDBC client with a test database
		JDBCClient client = JDBCClient.createShared(vertx, new JsonObject()
				.put("url", "jdbc:hsqldb:mem:test?shutdown=true")
				.put("driver_class", "org.hsqldb.jdbcDriver"));

		// config for sending emails
		MailConfig mailConfig = new MailConfig()
				.setHostname("smtp.gmail.com")
				.setPort(587)
				.setStarttls(StartTLSOptions.REQUIRED)
				.setLogin(LoginOption.REQUIRED).setAuthMethods("PLAIN")
				.setUsername("") // TODO fill in email acount + password to enable sending emails
				.setPassword(""); // TODO probably don't want to store this here in plaintext

		// create a mail client using the above config
		MailClient mailClient = MailClient.createShared(vertx, mailConfig);

		// load in initial data
		loadData(mongo);

		// create a new router
		Router router = Router.router(vertx);

		// We need cookies, sessions and request bodies
		router.route().handler(CookieHandler.create());
		router.route().handler(BodyHandler.create());
		router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));

		// Simple auth service which uses a JDBC data source
		AuthProvider authProvider = JDBCAuth.create(client);

		// We need a user session handler too to make sure the user is stored in
		// the session between requests
		router.route().handler(UserSessionHandler.create(authProvider));

		// Any requests to URI starting '/private/' require login
		router.route("/private/*").handler(RedirectAuthHandler.create(authProvider, "/loginpage.html"));

		// Serve the static private pages from directory 'private'
		router.route("/private/*").handler(StaticHandler.create().setCachingEnabled(false).setWebRoot("private"));

		// Handles the actual login
		router.route("/loginhandler").handler(FormLoginHandler.create(authProvider));

		// Handle a login (keep track of current user and their information)
		router.post("/login").handler(ctx -> {
			JsonObject credentials = ctx.getBodyAsJson();
			if (credentials == null) {
				// bad request
				ctx.fail(400);
				return;
			}

			// use the auth handler to perform the authentication for us
			authProvider.authenticate(credentials, login -> {
				// error handling
				if (login.failed()) {
					// forbidden
					ctx.fail(403);
					return;
				}

				// keep track of the current user
				ctx.setUser(login.result());
				currentUser = credentials.getString("username");

				// find the data associated with the user in the mongo database
				mongo.findOne("roomUsers", new JsonObject().put("username", credentials.getString("username")), null,
						lookup -> {
					// error handling
					if (lookup.failed()) {
						return;
					}

					JsonObject user = lookup.result();

					if (user == null) {
						// does not exist
						return;
					} else {
						ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json").end(user.encode());
					}
				});
			});
		});

		// Implement logout
		router.route("/logout").handler(context -> {
			context.clearUser();
			currentUser = "";
			// Redirect back to the index page
			context.response().putHeader("location", "/").setStatusCode(302).end();
		});

		// handle a user trying to make an account
		router.post("/api/signup").handler(ctx -> {
			JsonObject newUser = ctx.getBodyAsJson();

			String username = newUser.getString("username");
			// @TODO want to check passwords matching client side
			String password = newUser.getString("password");
			String confirm = newUser.getString("confirm");
			String email = newUser.getString("email");

			String salt = genSalt();
			String hashPwd = JDBCAuthImpl.computeHash(password, salt, "SHA-512");

			// add the user to the list of valid logins
			String sqlStatement = "insert into user values ('" + username + "', '" + hashPwd + "', '" + salt + "');";
			try {
				executeStatement(sqlStatement);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				// couldn't put into datebase ...
			}

			// initialize the user in the mongo database containing user info
			mongo.findOne("roomUsers", new JsonObject().put("username", newUser.getString("username")), null,
					lookup -> {
				// error handling
				if (lookup.failed()) {
					ctx.fail(500);
					return;
				}

				JsonObject user = lookup.result();

				// check if the user exists in the mongo database
				if (user != null) {
					// already exists, meaning we can't create the user
					System.out.println("user already exists");
					// TODO - JDBC doesn't save in between sessions, so for now
					// if it exists already its okay
					ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
					ctx.response().end(newUser.encode());
					// ctx.fail(500);
				} else {
					// user doesn't exist yet, create the user
					JsonObject insertUser = new JsonObject()
							.put("username", username)
							.put("email", email)
							.put("accountBalance", 0);
					
					// add the user into the database
					mongo.insert("roomUsers", insertUser, insert -> {
						// error handling
						if (insert.failed()) {
							ctx.fail(500);
							return;
						}

						// add the generated id to the user object
						insertUser.put("_id", insert.result());

						ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
						ctx.response().end(newUser.encode());
					});
				}
			});
		});

		// handle getting user information, given the user id (username)
		router.get("/api/user/:id").handler(ctx -> {
			String username = ctx.request().getParam("id");

			mongo.findOne("roomUsers", new JsonObject().put("username", username), null, lookup -> {
				// error handling
				if (lookup.failed()) {
					return;
				}

				JsonObject user = lookup.result();

				if (user == null) {
					// does not exist
					return;
				} else {
					ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json").end(user.encode());
				}
			});
		});

		// save the data when a user submits the main form
		router.post("/api/rooms").handler(ctx -> {
			// the form data is in the body
			JsonObject formData = ctx.getBodyAsJson();

			// save the essential data for when the actual search happens
			end = formData.getString("end");
			start = formData.getString("start");
			isCorner = formData.getString("isCorner");
			isPenthouse = formData.getString("isPenthouse");
			isWindow = formData.getString("isWindow");
			beds = formData.getInteger("beds");
			max = formData.getInteger("max");
			min = formData.getInteger("min");
			occupants = formData.getInteger("occupants");

			// once the data is saved, end the route
			ctx.response().end();
		});

		// handle searching for a room, and return the data
		router.get("/api/rooms").handler(ctx -> {
			final JsonArray json = new JsonArray();

			// try to parse the data from the form
			try {
				SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
				startDate = format.parse(start);
				endDate = format.parse(end);
			} catch (Exception e) {
				System.out.println("unable to parse dates");
			}

			// convert the user data for combo boxes into Booleans
			Boolean window = getBoolean(isWindow);
			Boolean corner = getBoolean(isCorner);
			Boolean penthouse = getBoolean(isPenthouse);

			// search the room manager for matching rooms
			for (JsonObject o : manager.getAvailableRooms(startDate, endDate, beds, corner, window, penthouse,
					occupants, max, min)) {
				// add each matching room to JsonArray to be displayed
				json.add(o);
			}

			ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
			ctx.response().end(json.encode());
		});

		// handle adding all rooms
		router.get("/api/allRooms").handler(ctx -> {
			final JsonArray json = new JsonArray();

			// no search parameters, just return all the rooms in the hotel
			for (JsonObject o : manager.getAllRooms()) {
				json.add(o);
			}

			ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
			ctx.response().end(json.encode());
		});

		// handle getting a list of bookings
		router.get("/api/bookings/:id").handler(ctx -> {
			String idStr = ctx.request().getParam("id");

			// check if we are looking for the bookings of a room, or of a user
			if (idStr.startsWith("user")) {
				// get the bookings for the user out of the mongo database
				mongo.find(currentUser, new JsonObject(), res -> {
					if (res.succeeded()) {
						final JsonArray json = new JsonArray();
						for (JsonObject obj : res.result()) {
							json.add(obj);
						}
						ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
						ctx.response().end(json.encode());
					} else {
						// lookup failed
						res.cause().printStackTrace();
						ctx.fail(500);
					}
				});
			} else {
				// add the bookings for the room
				final JsonArray json = new JsonArray();
				for (JsonObject o : manager.getBookings(Integer.parseInt(idStr))) {
					json.add(o);
				}
				ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
				ctx.response().end(json.encode());
			}
		});

		// handle adding money to the users account
		router.put("/api/money/:id").handler(ctx -> {
			JsonObject update = ctx.getBodyAsJson();
			double amount = Double.parseDouble(ctx.request().getParam("id"));
			final String username = update.getString("username");

			// find the user in the mongo database
			mongo.findOne("roomUsers", new JsonObject().put("username", username), null, lookup -> {
				// error handling
				if (lookup.failed()) {
					ctx.fail(500);
					return;
				}

				JsonObject user = lookup.result();

				if (user == null) {
					// does not exist
					ctx.fail(404);
				} else {
					// update the amount in the users account
					// TODO validate payment before adding to account
					user.put("accountBalance", amount + Double.parseDouble(user.getValue("accountBalance").toString()));

					// update the user in the mongo database
					mongo.replace("roomUsers", new JsonObject().put("username", username), user, replace -> {
						// error handling
						if (replace.failed()) {
							ctx.fail(500);
							return;
						}

						ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
						ctx.response().end(user.encode());
					});
				}
			});
		});

		// handle updating a user's name, email, and payment info in the mongo database
		router.put("/api/saveName/:id").handler(ctx -> {
			String username = ctx.request().getParam("id");
			mongo.findOne("roomUsers", new JsonObject().put("username", username), null, lookup -> {
				// error handling
				if (lookup.failed()) {
					ctx.fail(500);
					return;
				}

				JsonObject user = lookup.result();

				if (user == null) {
					// does not exist
					ctx.fail(404);
				} else {
					// update the user properties
					JsonObject update = ctx.getBodyAsJson();
					user.put("email", update.getString("email"));
					user.put("firstName", update.getString("firstName"));
					user.put("lastName", update.getString("lastName"));
					user.put("paymentInfo", update.getString("paymentInfo"));

					mongo.replace("roomUsers", new JsonObject().put("username", username), user, replace -> {
						// error handling
						if (replace.failed()) {
							ctx.fail(500);
							return;
						}

						ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
						ctx.response().end(user.encode());
					});
				}
			});
		});

		// handle saving a users room preferences
		router.put("/api/saveRoom/:id").handler(ctx -> {
			String username = ctx.request().getParam("id");

			mongo.findOne("roomUsers", new JsonObject().put("username", username), null, lookup -> {
				// error handling
				if (lookup.failed()) {
					ctx.fail(500);
					return;
				}

				JsonObject user = lookup.result();

				if (user == null) {
					// does not exist
					ctx.fail(404);
				} else {
					//update the users room preferences
					JsonObject room = ctx.getBodyAsJson();
					user.put("room", room);

					mongo.replace("roomUsers", new JsonObject().put("username", username), user, replace -> {
						// error handling
						if (replace.failed()) {
							ctx.fail(500);
							return;
						}

						ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
						ctx.response().end(user.encode());
					});
				}
			});
		});

		// handle validating the users payment before booking a room
		router.put("/api/book_room/:id").handler(ctx -> {
			JsonObject update = ctx.getBodyAsJson();
			boolean isAccount = update.getBoolean("paymentChecked");
			int id = Integer.parseInt(ctx.request().getParam("id"));

			// check whether the user is paying from their account or not
			if (isAccount) {
				// check the users account funds
				mongo.findOne("roomUsers", new JsonObject().put("username", currentUser), null, lookup -> {
					// error handling
					if (lookup.failed()) {
						ctx.fail(500);
						return;
					}

					JsonObject user = lookup.result();

					if (user == null) {
						// does not exist
						ctx.fail(404);
					} else {
						double price = manager.getPrice(id);
						double money = user.getDouble("accountBalance");

						// check if the account has enough money
						if (price > money) {
							System.out.println("Not enough funds - failing ctx and returning");
							ctx.fail(new IllegalArgumentException("Not Enough Funds"));
							return;
						} else {
							// if their was enough money, subtract the money and
							// update the user in the database
							money = money - price;
							user.put("accountBalance", money);

							mongo.replace("roomUsers", new JsonObject().put("username", currentUser), user, replace -> {
								// error handling
								if (replace.failed()) {
									ctx.fail(500);
									return;
								} else {
									// when updating the user succeeds, call the
									// next route to handle actually booking the
									// room
									ctx.next();
								}
							});
						}
					}
				});
			} else {
				// TODO mimic some type of payment validation on paymentInfo
				String paymentInfo = update.getString("paymentInfo");

				if (isPaymentValid(paymentInfo, manager.getPrice(id))) {
					ctx.next();
				} else {
					System.out.println("Payment invalid");
					ctx.fail(new IllegalArgumentException("Payment Invalid"));
					return;
				}
			}
		});

		// handle actually booking a room, once payment has been validated
		router.put("/api/book_room/:id").handler(ctx -> {
			JsonObject update = ctx.getBodyAsJson();
			String name = update.getString("firstName") + " " + update.getString("lastName");
			String emailAddr = update.getString("email");
			String paymentInfo = update.getString("paymentInfo");
			int id = Integer.parseInt(ctx.request().getParam("id"));

			// try to book the room
			if (manager.bookRoom(name, startDate, endDate, paymentInfo, id)) {
				// if the user if logged in, link the booking to them
				if (!currentUser.equals("")) {
					JsonObject booking = new JsonObject()
							.put("roomNumber", id)
							.put("start", startDate.toString())
							.put("end", endDate.toString());

					mongo.insert(currentUser, booking, res -> {
						System.out.println("inserted " + booking.encode());
					});
				}

				// build a confirmation email to send
				MailMessage email = new MailMessage()
						.setFrom("HotelReservationSystem@example.com")
						.setTo(emailAddr)
						.setSubject("Room Confirmation")
						.setText("Thank you for booking room: " + ctx.request().getParam("id"));

				// send the confirmation email
				mailClient.sendMail(email, result -> {
					if (result.failed()) {
						result.cause().printStackTrace();
					}
				});
			} else {
				ctx.fail(500);
				return;
			}

			ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
			ctx.response().end();
		}).failureHandler(ctx -> {
			// handle errors gracefully
			ctx.response().setStatusCode(500).end(ctx.failure().getMessage());
		});

		// Create a router endpoint for the static content.
		router.route().handler(StaticHandler.create());

		vertx.createHttpServer().requestHandler(router::accept).listen(8080);
	}

	/**
	 * @return a random 32 bit string to use for salting passwords
	 */
	public static String genSalt() {
		final Random r = new SecureRandom();
		byte[] salt = new byte[32];
		r.nextBytes(salt);
		return JDBCAuthImpl.bytesToHex(salt);
	}

	/**
	 * Helper method to understand the user selection on combo boxes
	 * 
	 * @param val
	 *            the string returned by the combo box in the form
	 * @return a Boolean representation of the selection
	 */
	private static Boolean getBoolean(String val) {
		Boolean boolVal = new Boolean(true);
		if (val == null) {
			boolVal = null;
		} else if (val.equals("2")) {
			boolVal = new Boolean(false);
		}
		return boolVal;
	}

	/**
	 * @param paymentInfo
	 *            the information for how the user is paying
	 * @param amount
	 *            the amount they need to pay
	 * @return whether or not their payment is valid
	 */
	private boolean isPaymentValid(String paymentInfo, double amount) {
		// TODO explore how validating payments is actually done
		return true;
	}

	/**
	 * Add initial data into the database (mostly for testing purposes)
	 * 
	 * @param url
	 * 			the url to the JDBC database to connect to
	 * @throws SQLException
	 * 			an exception that can occur while interacting with the database
	 */
	private void setUpInitialData(String url) throws SQLException {
		//connect to the database
		conn = DriverManager.getConnection(url);
		
		//delete any existing tables
		executeStatement("drop table if exists user;");
		executeStatement("drop table if exists user_roles;");
		executeStatement("drop table if exists roles_perms;");
		
		//create new tables
		executeStatement("create table user (username varchar(255), password varchar(255), password_salt varchar(255) );");
		executeStatement("create table user_roles (username varchar(255), role varchar(255));");
		executeStatement("create table roles_perms (role varchar(255), perm varchar(255));");

		//add sample user values into the new tables
		executeStatement("insert into user values ('tim', 'EC0D6302E35B7E792DF9DA4A5FE0DB3B90FCAB65A6215215771BF96D498A01DA8234769E1CE8269A105E9112F374FDAB2158E7DA58CDC1348A732351C38E12A0', 'C59EB438D1E24CACA2B1A48BC129348589D49303858E493FBE906A9158B7D5DC');");
		executeStatement("insert into user_roles values ('tim', 'dev');");
		executeStatement("insert into user_roles values ('tim', 'admin');");
		executeStatement("insert into roles_perms values ('dev', 'commit_code');");
		executeStatement("insert into roles_perms values ('dev', 'eat_pizza');");
		executeStatement("insert into roles_perms values ('admin', 'merge_pr');");
	}
	
	/**
	 * @param sql
	 * 			a sql statement to be executed
	 * @throws SQLException
	 */
	private void executeStatement(String sql) throws SQLException {
		conn.createStatement().execute(sql);
	}

	/**
	 * Load some sample data into the specified mongo database
	 *
	 * @param db
	 *            the database to load the sample data into
	 */
	private void loadData(MongoClient db) {
		// create a sample user preference for a room
		JsonObject timRoom = new JsonObject()
				.put("type", "0")
				.put("start", "2015-08-04")
				.put("end", "2015-08-05")
				.put("beds", 1)
				.put("isCorner", 1)
				.put("isWindow", 1)
				.put("isPenthouse", 1)
				.put("max", 1000)
				.put("min", 0)
				.put("occupants", 1);

		//create a sample user
		JsonObject user = new JsonObject()
				.put("_id", "1234") // give it an id so it doesn't keep adding each time
				.put("username", "tim")
				.put("firstName", "Tim")
				.put("lastName", "Lopes")
				.put("email", "BrettGavin889@gmail.com")
				.put("accountBalance", 10)
				.put("paymentInfo", "Info placeholder")
				.put("room", timRoom);

		//insert the sample user into the database
		db.insert("roomUsers", user, res -> {
			if (res.failed()) {
				System.out.println("failed insert: " + res.result());
			} else {
				System.out.println("inserted " + user.encode());
			}
		});
	}
}