package swarmBots;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import common.Coord;
import common.MapTile;
import common.ScanMap;
import enums.Science;
import enums.Terrain;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;

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
	int sleepTime;
	String SERVER_ADDRESS = "localhost";
	static final int PORT_ADDRESS = 9537;

    Set<Coord> passedTile;

	public ROVER_16() {
		// constructor
		System.out.println("ROVER_16 rover object constructed");
		rovername = "ROVER_16";
		SERVER_ADDRESS = "localhost";
		// this should be a safe but slow timer value
		sleepTime = 300; // in milliseconds - smaller is faster, but the server will cut connection if it is too small

        passedTile = new HashSet<>();
	}

	public ROVER_16(String serverAddress) {
		// constructor
		System.out.println("ROVER_16 rover object constructed");
		rovername = "ROVER_16";
		SERVER_ADDRESS = serverAddress;
		sleepTime = 200; // in milliseconds - smaller is faster, but the server will cut connection if it is too small

        passedTile = new HashSet<>();
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
			
	
			// Process all messages from server, wait until server requests Rover ID
			// name - Return Rover Name to complete connection
			while (true) {
				String line = in.readLine();
				if (line.startsWith("SUBMITNAME")) {
					out.println(rovername); // This sets the name of this instance
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
			 *  Get initial values that won't change
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
			
			

			
	
	
			boolean goingSouth = false;
            boolean goingEast = false;
            boolean goingNorth = false;
            boolean goingWest = false;
            boolean arriveTarget = false;
            boolean park = false;
			boolean stuck = false; // just means it did not change locations between requests,
									// could be velocity limit or obstruction etc.
			boolean blocked = false;
	
			String[] cardinals = new String[4];
			cardinals[0] = "N";
			cardinals[1] = "E";
			cardinals[2] = "S";
			cardinals[3] = "W";
	
			String currentDir = cardinals[0];
			Coord currentLoc = null;
			Coord previousLoc = null;
	

			/**
			 *  ####  Rover controller process loop  ####
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
					// loc = line.substring(4);
					currentLoc = extractLocationFromString(line);
					
				}
				System.out.println(rovername + " currentLoc at start: " + currentLoc);

                //record passed coordinate
                if(!arriveTarget && inBox(currentLoc, targetLocation)){
                    System.out.println("arrive target box");
                    arriveTarget = true;
                    //clear passed mapTile when arrive target-area
                    passedTile.clear();
                }
                if(arriveTarget && inBox(currentLoc, rovergroupStartPosition)){
                    System.out.println("park in start square");
                    return;
                }
                passedTile.add(currentLoc);
				
				// after getting location set previous equal current to be able to check for stuckness and blocked later
				previousLoc = currentLoc;		
				


				// ***** do a SCAN *****

				// gets the scanMap from the server based on the Rover current location
				doScan(); 
				// prints the scanMap to the Console output for debug purposes
				scanMap.debugPrintMap();
				
		
				
				
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
				
				
	
				
				// ***** MOVING *****

                // pull the MapTile array out of the ScanMap object
                MapTile[][] scanMapTiles = scanMap.getScanMap();
                int centerIndex = (scanMap.getEdgeSize() - 1)/2;
                // tile S = y + 1; N = y - 1; E = x + 1; W = x - 1

                //gather science if currentLoc has
                if(scanMapTiles[centerIndex][centerIndex].getScience() == Science.RADIOACTIVE){
                    out.print("GATHER");
                }

                //find an appropriate direction to move
                boolean hasScience = false;
                Coord originPos;
                Coord nextMapPos;
                if((nextMapPos = findFirstScience(scanMapTiles)) != null){
                    //move to science first
                    hasScience = true;
                    originPos = new Coord(centerIndex, centerIndex);
                }else if(!arriveTarget){
                    //move to target if never arrived
                    originPos = currentLoc;
                    nextMapPos = new Coord(targetLocation.xpos, targetLocation.ypos);
                }
                else {
                    //else try to move to start position
                    originPos = currentLoc;
                    nextMapPos = rovergroupStartPosition;
                }
                //calculate roughly direction
                goingSouth = isSouth(originPos, nextMapPos);
                goingEast = isEast(originPos, nextMapPos);
                goingNorth = isNorth(originPos, nextMapPos);
                goingWest = isWest(originPos, nextMapPos);

                //check 4 dir is ok or blocked
                boolean northOk = isPosOk(scanMapTiles, centerIndex, centerIndex - 1);
                boolean southOk = isPosOk(scanMapTiles, centerIndex, centerIndex + 1);
                boolean eastOk = isPosOk(scanMapTiles, centerIndex + 1, centerIndex);
                boolean westOk = isPosOk(scanMapTiles, centerIndex - 1, centerIndex);

                if (goingSouth && southOk
                        && !passedTile.contains(new Coord(currentLoc.xpos, currentLoc.ypos + 1))
                        ) {
                    // check scanMap to see if path is blocked to the south
                    // and if path is ever passed
                    // (scanMap may be old data by now)
                        // request to server to move
                        out.println("MOVE S");
                        //System.out.println("ROVER_16 request move S");

                } else if(goingEast && eastOk
                        && !passedTile.contains(new Coord(currentLoc.xpos + 1, currentLoc.ypos))
                        ){
                    // check scanMap to see if path is blocked to the east
                    // and if path is ever passed
                    // (scanMap may be old data by now)
                        // request to server to move
                        out.println("MOVE E");
                        //System.out.println("ROVER_16 request move N");
                }
                else if (goingNorth && northOk
                        && !passedTile.contains(new Coord(currentLoc.xpos, currentLoc.ypos - 1))
                        ){
                    // check scanMap to see if path is blocked to the north
                    out.println("MOVE N");
                }else if(goingWest && westOk
                        && !passedTile.contains(new Coord(currentLoc.xpos - 1, currentLoc.ypos))){
                    //exclude all-blocked-direction move west
                    out.println("MOVE W");
                }else{
                    //when around with passedTile or last-move is stuck
                    moveRandom(northOk, southOk, eastOk, westOk);
                }

				// another call for current location
				out.println("LOC");
				line = in.readLine();
				if(line == null){
					System.out.println("ROVER_16 check connection to server");
					line = "";
				}
				if (line.startsWith("LOC")) {
					currentLoc = extractLocationFromString(line);
				}

	
				// test for stuckness
				stuck = currentLoc.equals(previousLoc);

				System.out.println("ROVER_16 stuck test " + stuck);
//				System.out.println("ROVER_16 blocked test " + blocked);
	

				// this is the Rovers HeartBeat, it regulates how fast the Rover cycles through the control loop
				Thread.sleep(sleepTime);
				
				System.out.println("ROVER_16 ------------ bottom process control --------------"); 
			}
		
		// This catch block closes the open socket connection to the server
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
	        if (socket != null) {
	            try {
	            	socket.close();
	            } catch (IOException e) {
	            	System.out.println("ROVER_16 problem closing socket");
	            }
	        }
	    }

	} // END of Rover main control loop


    // ####################### Support Methods #############################
	
	private void clearReadLineBuffer() throws IOException{
		while(in.ready()){
			//System.out.println("ROVER_16 clearing readLine()");
			in.readLine();	
		}
	}
	

	// method to retrieve a list of the rover's EQUIPMENT from the server
	private ArrayList<String> getEquipment() throws IOException {
		//System.out.println("ROVER_16 method getEquipment()");
		Gson gson = new GsonBuilder()
    			.setPrettyPrinting()
    			.enableComplexMapKeySerialization()
    			.create();
		out.println("EQUIPMENT");
		
		String jsonEqListIn = in.readLine(); //grabs the string that was returned first
		if(jsonEqListIn == null){
			jsonEqListIn = "";
		}
		StringBuilder jsonEqList = new StringBuilder();
		//System.out.println("ROVER_16 incomming EQUIPMENT result - first readline: " + jsonEqListIn);
		
		if(jsonEqListIn.startsWith("EQUIPMENT")){
			while (!(jsonEqListIn = in.readLine()).equals("EQUIPMENT_END")) {
				if(jsonEqListIn == null){
					break;
				}
				//System.out.println("ROVER_16 incomming EQUIPMENT result: " + jsonEqListIn);
				jsonEqList.append(jsonEqListIn);
				jsonEqList.append("\n");
				//System.out.println("ROVER_16 doScan() bottom of while");
			}
		} else {
			// in case the server call gives unexpected results
			clearReadLineBuffer();
			return null; // server response did not start with "EQUIPMENT"
		}
		
		String jsonEqListString = jsonEqList.toString();		
		ArrayList<String> returnList;		
		returnList = gson.fromJson(jsonEqListString, new TypeToken<ArrayList<String>>(){}.getType());		
		//System.out.println("ROVER_16 returnList " + returnList);
		
		return returnList;
	}
	

	// sends a SCAN request to the server and puts the result in the scanMap array
	public void doScan() throws IOException {
		//System.out.println("ROVER_16 method doScan()");
		Gson gson = new GsonBuilder()
    			.setPrettyPrinting()
    			.enableComplexMapKeySerialization()
    			.create();
		out.println("SCAN");

		String jsonScanMapIn = in.readLine(); //grabs the string that was returned first
		if(jsonScanMapIn == null){
			System.out.println("ROVER_16 check connection to server");
			jsonScanMapIn = "";
		}
		StringBuilder jsonScanMap = new StringBuilder();
		System.out.println("ROVER_16 incomming SCAN result - first readline: " + jsonScanMapIn);
		
		if(jsonScanMapIn.startsWith("SCAN")){	
			while (!(jsonScanMapIn = in.readLine()).equals("SCAN_END")) {
				//System.out.println("ROVER_16 incomming SCAN result: " + jsonScanMapIn);
				jsonScanMap.append(jsonScanMapIn);
				jsonScanMap.append("\n");
				//System.out.println("ROVER_16 doScan() bottom of while");
			}
		} else {
			// in case the server call gives unexpected results
			clearReadLineBuffer();
			return; // server response did not start with "SCAN"
		}
		//System.out.println("ROVER_16 finished scan while");

		String jsonScanMapString = jsonScanMap.toString();
		// debug print json object to a file
		//new MyWriter( jsonScanMapString, 0);  //gives a strange result - prints the \n instead of newline character in the file

		//System.out.println("ROVER_16 convert from json back to ScanMap class");
		// convert from the json string back to a ScanMap object
		scanMap = gson.fromJson(jsonScanMapString, ScanMap.class);		
	}
	

	// this takes the server response string, parses out the x and x values and
	// returns a Coord object	
	public static Coord extractLocationFromString(String sStr) {
		int indexOf;
		indexOf = sStr.indexOf(" ");
		sStr = sStr.substring(indexOf +1);
		if (sStr.lastIndexOf(" ") != -1) {
			String xStr = sStr.substring(0, sStr.lastIndexOf(" "));
			//System.out.println("extracted xStr " + xStr);

			String yStr = sStr.substring(sStr.lastIndexOf(" ") + 1);
			//System.out.println("extracted yStr " + yStr);
			return new Coord(Integer.parseInt(xStr), Integer.parseInt(yStr));
		}
		return null;
	}


	/**
	 * check that 2nd coord is south of the 1st coord or not
     * @return
     */
	private boolean isSouth(Coord coord1, Coord coord2) {
		return coord2.ypos > coord1.ypos;
	}
	private boolean isNorth(Coord coord1, Coord coord2) {
		return coord2.ypos < coord1.ypos;
	}
	private boolean isEast(Coord coord1, Coord coord2) {
		return coord2.xpos > coord1.xpos;
	}
	private boolean isWest(Coord coord1, Coord coord2) {
		return coord2.xpos < coord1.xpos;
	}


    //is scanMapTile[x][y] is ok or blocked
    boolean isPosOk(MapTile[][] scanMapTiles, int x, int y){
        return !(scanMapTiles[x][y].getHasRover()
                || scanMapTiles[x][y].getTerrain() == Terrain.SAND
                || scanMapTiles[x][y].getTerrain() == Terrain.NONE);
    }

    // move random available step
    private void moveRandom(boolean north, boolean south, boolean east, boolean west) {
        List<String> availableDir = new ArrayList<>();
        if(north) availableDir.add("N");
        if(south) availableDir.add("S");
        if(east) availableDir.add("E");
        if(west) availableDir.add("W");
        out.println("MOVE " + availableDir.get(new Random().nextInt(availableDir.size())));
    }

    //find science from scanMap
    private Coord findFirstScience(MapTile[][] scanMapTiles) {
        for (int i = 0; i < scanMapTiles.length; i++) {
            for (int j = 0; j < scanMapTiles[i].length; j++) {
                if(scanMapTiles[i][j].getScience() == Science.RADIOACTIVE &&
                        scanMapTiles[i][j].getTerrain() != Terrain.SAND){
                    return new Coord(i, j);
                }
            }
        }
        return null;
    }

    //check target in center's 7x7 box
    boolean inBox(Coord target, Coord center){
        if(target.xpos >= center.xpos - 3 && target.xpos <= center.xpos + 3
                && target.ypos >= center.ypos - 3 && target.ypos <= center.ypos + 3 ){
            return true;
        }
        return false;
    }

	/**
	 * Runs the client
	 */
	public static void main(String[] args) throws Exception {
        ROVER_16 client = null;
        if (args.length > 0) {
            client = new ROVER_16(args[0]);
        }else{
            client = new ROVER_16();
        }
        client.run();
    }
}