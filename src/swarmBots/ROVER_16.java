package swarmBots;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.swing.event.ListSelectionEvent;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import common.Communication;
import common.Coord;
import common.MapTile;
import common.ScanMap;
import enums.RoverDriveType;
import enums.RoverToolType;
import enums.Terrain;
import supportTools.AStar;

/**
 * The seed that this program is built on is a chat program example found here:
 * http://cs.lmu.edu/~ray/notes/javanetexamples/ Many thanks to the authors for
 * publishing their code examples
 */

public class ROVER_16 {

	BufferedReader in;
	PrintWriter out;
	String rovername;
	ScanMap scanMap;
	public static Map<Coord, MapTile> globalMap;
	int sleepTime = 1200;
	String SERVER_ADDRESS = "192.168.1.106";
	String commIP = "192.168.1.104";
	List<Coord> destinations;
	static final int PORT_ADDRESS = 9537;
	long trafficCounter;

	String direction = "E";

	public ROVER_16() {
		// constructor
		System.out.println("ROVER_16 rover object constructed");
		rovername = "ROVER_16";
		// SERVER_ADDRESS = "localhost";
		// this should be a safe but slow timer value
		sleepTime = 300; // in milliseconds - smaller is faster, but the server
							// will cut connection if it is too small
		destinations = new ArrayList<>();
		globalMap = new HashMap<>();
	}

	public ROVER_16(String serverAddress) {
		// constructor
		System.out.println("ROVER_16 rover object constructed");
		rovername = "ROVER_16";
		SERVER_ADDRESS = serverAddress;
		sleepTime = 200; // in milliseconds - smaller is faster, but the server
							// will cut connection if it is too small
		destinations = new ArrayList<>();
		globalMap = new HashMap<>();
	}

	/**
	 * Connects to the server then enters the processing loop.
	 */
	private void run() throws IOException, InterruptedException {
		// Make connection to SwarmServer and initialize streams
		Socket socket = null;
		try {
			socket = new Socket(SERVER_ADDRESS, PORT_ADDRESS);

			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new PrintWriter(socket.getOutputStream(), true);

			// Process all messages from server, wait until server requests
			// Rover ID
			// name - Return Rover Name to complete connection
			while (true) {
				String line = in.readLine();
				if (line.startsWith("SUBMITNAME")) {
					out.println(rovername); // This sets the name of this
											// instance
											// of a swarmBot for identifying the
											// thread to the server
					break;
				}
			}

			// ********* Rover logic setup *********

			String line = "";
			Coord rovergroupStartPosition = null;
			Coord targetLocation = null;

			/**
			 * Get initial values that won't change
			 */
			// **** get equipment listing ****
			ArrayList<String> equipment = new ArrayList<String>();
			equipment = getEquipment();
			System.out.println(rovername + " equipment list results " + equipment + "\n");

			// **** Request START_LOC Location from SwarmServer ****
			out.println("START_LOC");
			line = in.readLine();
			if (line == null) {
				System.out.println(rovername + " check connection to server");
				line = "";
			}
			if (line.startsWith("START_LOC")) {
				rovergroupStartPosition = extractLocationFromString(line);
			}
			System.out.println(rovername + " START_LOC " + rovergroupStartPosition);

			// **** Request TARGET_LOC Location from SwarmServer ****
			out.println("TARGET_LOC");
			line = in.readLine();
			if (line == null) {
				System.out.println(rovername + " check connection to server");
				line = "";
			}
			if (line.startsWith("TARGET_LOC")) {
				targetLocation = extractLocationFromString(line);
			}
			System.out.println(rovername + " TARGET_LOC " + targetLocation);

			boolean stuck = false; // just means it did not change locations
									// between requests,
									// could be velocity limit or obstruction
									// etc.

			// ******** communication server
			String url = "http://" + commIP + ":3000/api";
			String corp_secret = "gz5YhL70a2";
			Communication com = new Communication(url, rovername, corp_secret);

			String[] cardinals = new String[4];
			cardinals[0] = "N";
			cardinals[1] = "E";
			cardinals[2] = "S";
			cardinals[3] = "W";

			Coord currentLoc = null;
			Coord previousLoc = null;
			destinations.add(targetLocation);
			
			Coord destination = null;

			/**
			 * #### Rover controller process loop ####
			 */
			while (true) {
				// **** Request Rover Location from SwarmServer ****
				out.println("LOC");
				line = in.readLine();
				if (line == null) {
					System.out.println(rovername + " check connection to server");
					line = "";
				}
				if (line.startsWith("LOC")) {
					currentLoc = extractLocationFromString(line);
				}
				System.out.println(rovername + " currentLoc at start: " + currentLoc);

				// after getting location set previous equal current to be able
				// to check for stuckness and blocked later
				previousLoc = currentLoc;

				doScan();
				scanMap.debugPrintMap();
				MapTile[][] scanMapTiles = scanMap.getScanMap();
				updateglobalMap(currentLoc, scanMapTiles);
				
				com.postScanMapTiles(currentLoc, scanMapTiles);
				
				// ***** get TIMER remaining *****
				out.println("TIMER");
				line = in.readLine();
				if (line == null) {
					System.out.println(rovername + " check connection to server");
					line = "";
				}
				if (line.startsWith("TIMER")) {
					String timeRemaining = line.substring(6);
					System.out.println(rovername + " timeRemaining: " + timeRemaining);
				}
				
				if (trafficCounter % 10 == 0) {
	                //updateglobalMap(com.getGlobalMap());

	                // ********* get closest destination from current location everytime
	                if (!destinations.isEmpty()) {
	                    destination = getClosestDestination(currentLoc);
	                }

	            }
	            trafficCounter++;

				// MOVING
	         // if no destination, wait
	            // TODO: use this time meaningfully
	            if (destination == null){

	                if (!destinations.isEmpty()){
	                    destination = getClosestDestination(currentLoc);
	                }

	            } else {
	                List<String> moves = AStar.Astar(currentLoc, destination, scanMapTiles, RoverDriveType.TREADS, globalMap);
	                System.out.println(rovername + "currentLoc: " + currentLoc + ", destination: " + destination);
	                System.out.println(rovername + " moves: " + moves.toString());
	//

	                // if STILL MOVING
	                if (!moves.isEmpty()) {
	                    out.println("MOVE " + moves.get(0));

	                    // if rover is next to the target
	                    // System.out.println("Rover near destiation. distance: " + getDistance(currentLoc, destination));
	                    if (AStar.getDistance(currentLoc, destination) < 301) {
	                        System.out.println("Target visible.");

	                        // if destination is walkable
	                        if (AStar.validateTile(globalMap.get(destination), RoverDriveType.TREADS)) {
	                            System.out.println("Target Reachable");
	                        } else {
	                            // Target is not walkable (hasRover, or sand)
	                            // then go to next destination, push current destination to end
	                            // TODO: handle the case when the destiation is blocked permanently
	                            // TODO: also, what if the destination is already taken? update globalMap and dont go there

	                            // move to new destination
	                            destinations.remove(destination);
	                            destination = getClosestDestination(currentLoc);
	                            System.out.println("Target blocked. Switch target to: " + destination);
	                        }

	                    }


	                    // IF NO MORE MOVES, it can mean several things:
	                    // 1. we are at the destination
	                    // 2. blocked? error?
	                } else {
	                    // check if rover is at the destination, drill
	                    if (currentLoc.equals(destination)) {
	                        out.println("GATHER");
	                        System.out.println(rovername + " arrived destination. Now gathering.");
	                        if (!destinations.isEmpty()) {
	                            //remove from destinations
	                            destinations.remove(destination);
	                            destination = getClosestDestination(currentLoc);
	                            System.out.println(rovername + " going to next destination at: " + destination);
	                        } else {
	                            System.out.println("Nowhere else to go. Relax..");
	                        }

	                    } else {

	                    }
	                }
	            }
				

				// another call for current location
				out.println("LOC");
				line = in.readLine();
				if (line == null) {
					System.out.println("ROVER_16 check connection to server");
					line = "";
				}
				if (line.startsWith("LOC")) {
					currentLoc = extractLocationFromString(line);
				}
				out.println("GATHER");
				System.out.println("ROVER_16 currentLoc after recheck: " + currentLoc);
				System.out.println("ROVER_16 previousLoc: " + previousLoc);

				System.out.println("ROVER_16 stuck test " + stuck);

				doScan();
				/* *************************************************/

				Thread.sleep(sleepTime);
			}
		}

		catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	// ####################### Support Methods #############################
	
	private void updateglobalMap(Coord currentLoc, MapTile[][] scanMapTiles) {
        int centerIndex = (scanMap.getEdgeSize() - 1) / 2;

        for (int row = 0; row < scanMapTiles.length; row++) {
            for (int col = 0; col < scanMapTiles[row].length; col++) {

                MapTile mapTile = scanMapTiles[col][row];

                int xp = currentLoc.xpos - centerIndex + col;
                int yp = currentLoc.ypos - centerIndex + row;
                Coord coord = new Coord(xp, yp);
                globalMap.put(coord, mapTile);
            }
        }
        // put my current position so it is walkable
        MapTile currentMapTile = scanMapTiles[centerIndex][centerIndex].getCopyOfMapTile();
        //currentMapTile.setHasRoverFalse();
        globalMap.put(currentLoc, currentMapTile);
    }
	

	
	private Coord getClosestDestination(Coord currentLoc) {
        double max = Double.MAX_VALUE;
        Coord target = null;

        for (Coord desitnation : destinations) {
            double distance = AStar.getDistance(currentLoc, desitnation);
            if (distance < max) {
                max = distance;
                target = desitnation;
            }
        }
        return target;
    }

	private void clearReadLineBuffer() throws IOException {
		while (in.ready()) {
			// System.out.println("ROVER_16 clearing readLine()");
			in.readLine();
		}
	}

	// method to retrieve a list of the rover's EQUIPMENT from the server
	private ArrayList<String> getEquipment() throws IOException {
		// System.out.println("ROVER_16 method getEquipment()");
		Gson gson = new GsonBuilder().setPrettyPrinting().enableComplexMapKeySerialization().create();
		out.println("EQUIPMENT");

		String jsonEqListIn = in.readLine(); // grabs the string that was
												// returned first
		if (jsonEqListIn == null) {
			jsonEqListIn = "";
		}
		StringBuilder jsonEqList = new StringBuilder();
		// System.out.println("ROVER_16 incoming EQUIPMENT result - first
		// readline: " + jsonEqListIn);

		if (jsonEqListIn.startsWith("EQUIPMENT")) {
			while (!(jsonEqListIn = in.readLine()).equals("EQUIPMENT_END")) {
				if (jsonEqListIn == null) {
					break;
				}
				// System.out.println("ROVER_16 incoming EQUIPMENT result: " +
				// jsonEqListIn);
				jsonEqList.append(jsonEqListIn);
				jsonEqList.append("\n");
				// System.out.println("ROVER_16 doScan() bottom of while");
			}
		} else {
			// in case the server call gives unexpected results
			clearReadLineBuffer();
			return null; // server response did not start with "EQUIPMENT"
		}

		String jsonEqListString = jsonEqList.toString();
		ArrayList<String> returnList;
		returnList = gson.fromJson(jsonEqListString, new TypeToken<ArrayList<String>>() {
		}.getType());
		// System.out.println("ROVER_16 returnList " + returnList);

		return returnList;
	}

	// sends a SCAN request to the server and puts the result in the scanMap
	// array
	public void doScan() throws IOException {
		// System.out.println("ROVER_16 method doScan()");
		Gson gson = new GsonBuilder().setPrettyPrinting().enableComplexMapKeySerialization().create();
		out.println("SCAN");

		String jsonScanMapIn = in.readLine(); // grabs the string that was
												// returned first
		if (jsonScanMapIn == null) {
			System.out.println("ROVER_16 check connection to server");
			jsonScanMapIn = "";
		}
		StringBuilder jsonScanMap = new StringBuilder();
		System.out.println("ROVER_16 incomming SCAN result - first readline: " + jsonScanMapIn);

		if (jsonScanMapIn.startsWith("SCAN")) {
			while (!(jsonScanMapIn = in.readLine()).equals("SCAN_END")) {
				// System.out.println("ROVER_16 incoming SCAN result: " +
				// jsonScanMapIn);
				jsonScanMap.append(jsonScanMapIn);
				jsonScanMap.append("\n");
				// System.out.println("ROVER_16 doScan() bottom of while");
			}
		} else {
			// in case the server call gives unexpected results
			clearReadLineBuffer();
			return; // server response did not start with "SCAN"
		}
		// System.out.println("ROVER_16 finished scan while");

		String jsonScanMapString = jsonScanMap.toString();
		// debug print json object to a file
		// new MyWriter( jsonScanMapString, 0); //gives a strange result -
		// prints the \n instead of newline character in the file

		// System.out.println("ROVER_16 convert from json back to ScanMap
		// class");
		// convert from the json string back to a ScanMap object
		scanMap = gson.fromJson(jsonScanMapString, ScanMap.class);
	}

	// this takes the server response string, parses out the x and x values and
	// returns a Coord object
	public static Coord extractLocationFromString(String sStr) {
		int indexOf;
		indexOf = sStr.indexOf(" ");
		sStr = sStr.substring(indexOf + 1);
		if (sStr.lastIndexOf(" ") != -1) {
			String xStr = sStr.substring(0, sStr.lastIndexOf(" "));
			// System.out.println("extracted xStr " + xStr);

			String yStr = sStr.substring(sStr.lastIndexOf(" ") + 1);
			// System.out.println("extracted yStr " + yStr);
			return new Coord(Integer.parseInt(xStr), Integer.parseInt(yStr));
		}
		return null;
	}

	/**
	 * Runs the client
	 */
	public static void main(String[] args) throws Exception {
		ROVER_16 client = new ROVER_16();
		client.run();
	}
}