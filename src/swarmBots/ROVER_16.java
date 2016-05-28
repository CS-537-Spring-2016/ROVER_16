package swarmBots;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import common.Coord;
import common.MapTile;
import common.ScanMap;
import enums.RoverDriveType;
import enums.RoverToolType;
import enums.Terrain;

/**
 * The seed that this program is built on is a chat program example found here:
 * http://cs.lmu.edu/~ray/notes/javanetexamples/ Many thanks to the authors for
 * publishing their code examples
 */

public class ROVER_16  
{

	BufferedReader in;
	PrintWriter out;
	String rovername;
	ScanMap scanMap;
	int sleepTime;
	String SERVER_ADDRESS = "localhost";
	static final int PORT_ADDRESS = 9537;
	
	String direction = "E";
	

	public ROVER_16 () 
	{
		// constructor
		System.out.println("ROVER_16 rover object constructed");
		rovername = "ROVER_16";
		SERVER_ADDRESS = "localhost";
		// this should be a safe but slow timer value
		sleepTime = 300; // in milliseconds - smaller is faster, but the server will cut connection if it is too small
	}
	
	public ROVER_16(String serverAddress) 
	{
		// constructor
		System.out.println("ROVER_16 rover object constructed");
		rovername = "ROVER_16";
		SERVER_ADDRESS = serverAddress;
		sleepTime = 200; // in milliseconds - smaller is faster, but the server will cut connection if it is too small
	}

	/**
	 * Connects to the server then enters the processing loop.
	 */
	private void run() throws IOException, InterruptedException
	{

		// Make connection to SwarmServer and initialize streams
		Socket socket = null;
		try 
		{
			socket = new Socket(SERVER_ADDRESS, PORT_ADDRESS);

			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new PrintWriter(socket.getOutputStream(), true);
			
	
			// Process all messages from server, wait until server requests Rover ID
			// name - Return Rover Name to complete connection
			while (true) 
			{
				String line = in.readLine();
				if (line.startsWith("SUBMITNAME")) 
				{
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
            if (line == null) 
            {
            	System.out.println(rovername + " check connection to server");
            	line = "";
            }
			if (line.startsWith("START_LOC")) 
			{
				rovergroupStartPosition = extractLocationFromString(line);
			}
			System.out.println(rovername + " START_LOC " + rovergroupStartPosition);
			
			// **** Request TARGET_LOC Location from SwarmServer ****
			out.println("TARGET_LOC");
			line = in.readLine();
            if (line == null) 
            {
            	System.out.println(rovername + " check connection to server");
            	line = "";
            }
			if (line.startsWith("TARGET_LOC")) 
			{
				targetLocation = extractLocationFromString(line);
			}
			System.out.println(rovername + " TARGET_LOC " + targetLocation);
			
	
			boolean goingSouth = false;
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
	
			int northMove = 1;
			int eastMove = 31;
			int southMove = 5;
			int nextEastMove = 16;
			int nextNorthMove = 6;

			/**
			 *  ####  Rover controller process loop  ####
			 */
			while (true) 
			{	
				// **** Request Rover Location from SwarmServer ****
				out.println("LOC");
				line = in.readLine();
	            if (line == null) 
	            {
	            	System.out.println(rovername + " check connection to server");
	            	line = "";
	            }
				if (line.startsWith("LOC"))
				{
					// loc = line.substring(4);
					currentLoc = extractLocationFromString(line);
					
				}
				System.out.println(rovername + " currentLoc at start: " + currentLoc);
				
				// after getting location set previous equal current to be able to check for stuckness and blocked later
				previousLoc = currentLoc;		
	
				
				doScan();
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
				
	

				// MOVING

				// Code for motion of the Rover.
				if (northMove != 0) {
					out.println("MOVE N");
					northMove--;
				} else if (eastMove != 0) {
					out.println("MOVE E");
					eastMove--;
				} else if (southMove != 0) {
					out.println("MOVE S");
					southMove--;
				}else if(nextEastMove!=0){
					out.println("MOVE E");
					nextEastMove--;
				}else if(nextNorthMove!=0){
					out.println("MOVE N");
					nextNorthMove--;
				}else
					Next_Move(currentLoc); 
				
				/*
				for(int i = 0; i< 10; i++)
				{
					for(int j = 0; j<10; j++)
					{
						System.out.print(scanMapTiles[i][j].getHasRover());
					}
					System.out.println();
				}
				*/
				// another call for current location
				out.println("LOC");
				line = in.readLine();
				if(line == null){
					System.out.println("ROVER_00 check connection to server");
					line = "";
				}
				if (line.startsWith("LOC")) 
				{
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
		
		catch (Exception e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	
	
	public void Next_Move(Coord currentLoc) throws IOException 
	{
		MapTile[][] scanMapTiles = scanMap.getScanMap();
		int centerIndex = (scanMap.getEdgeSize() - 1)/2;
		
		
		int x_cord, y_cord;
		//x_cord = currentLoc.xpos;
		//y_cord = currentLoc.ypos;
		x_cord = y_cord = centerIndex;
	    
		System.out.println("x_cord: " + x_cord + "y_cord: " + y_cord + "direction: " + direction);
		if(Check_Valid_Move(scanMapTiles, x_cord, y_cord))
		{
			move(direction);	
		}
		else
		{
			while(true)
			{
				direction = Change_Direction();
				
				if(Check_Valid_Move(scanMapTiles, x_cord, y_cord))
				{
					move(direction);
					break;
				}
			}
		}
		
	}
	
	public boolean Check_Valid_Move(MapTile[][] scanMapTiles, int x, int y)
	{
		switch(direction)
		{
		case "E":
			System.out.println("East");
			x = x+1;
			System.out.println("x: " + x + "y: " + y);
			break;
		case "W":
			System.out.println("West");
			x = x-1;
			System.out.println("x: " + x + "y: " + y);
			break;
		case "N":
			System.out.println("North");
			y = y-1;
			System.out.println("x: " + x + "y: " + y);
			break;
		case "S":
			System.out.println("South");
			y = y+1;
			System.out.println("x: " + x + "y: " + y);
			break;
			
		}
		
		if (scanMapTiles[x][y].getTerrain() == Terrain.SAND || scanMapTiles[x][y].getTerrain() == Terrain.NONE 
				|| scanMapTiles[x][y].getHasRover() == true)
			
		{
			System.out.println("getTerrain: " + scanMapTiles[x][y].getTerrain());
			System.out.println("gethasrover: " + scanMapTiles[x][y].getHasRover());
			System.out.println("false");
			return false;
		}
		else
		{
			System.out.println("true");
			return true;
		}
	}
	
	public void move(String direction) 
	{
		out.println("MOVE " + direction);
	}
	
	public String Change_Direction()
	{
		Random randomGenerator = new Random();
	    int randomInt = randomGenerator.nextInt(1000);
	    randomInt %= 4;
	    
	    switch(randomInt)
	    {
	    case 0:
	    	direction =  "E";
	    	break;
	    case 1:
	    	direction =  "W";
	    	break;
	    case 2:
	    	direction =  "N";
	    	break;
	    case 3:
	    	direction =  "S";
	    	break;
	 }
	/*	
	System.out.println("Change direction");
	
	if(direction == "W" )
	{
		direction = "S";
	}
	else if (direction == "S")
	{
		direction = "E";
	}
	else if (direction == "E")
	{
		direction = "N";
		if(Check_Valid_Move(scanMapTiles, x, y))
		{
			direction = "N";
		}
		else
		{
			direction = "S";
		}
	}
	else
	{
		direction = "E";
	}
	*/
	 return direction;
	}
	/*
	public boolean check_5_moves(MapTile[][] scanMapTiles, int x_cord, int y_cord)
	{
		System.out.println("Check_5_moves");
		int flag = 0;
		
		if (!(scanMapTiles[x_cord][y_cord+1].getTerrain() == Terrain.SAND || 
			scanMapTiles[x_cord][y_cord+1].getTerrain() == Terrain.NONE ||
			scanMapTiles[x_cord][y_cord+1].getHasRover() == true))
		{
			flag++;
			if (!(scanMapTiles[x_cord][y_cord+2].getTerrain() == Terrain.SAND || 
				scanMapTiles[x_cord][y_cord+2].getTerrain() == Terrain.NONE ||
				scanMapTiles[x_cord][y_cord+2].getHasRover() == true))
			{
				flag++;
				if (!(scanMapTiles[x_cord][y_cord+3].getTerrain() == Terrain.SAND || 
					scanMapTiles[x_cord][y_cord+3].getTerrain() == Terrain.NONE ||
					scanMapTiles[x_cord][y_cord+3].getHasRover() == true))
				{
					flag++;
				}
			}
	    }
		if(flag == 4)
		{
			System.out.println("1 - True");
			return true;
		}
		else 
		{
			System.out.println("1 - false");
			return false;
		}
	}
	*/
	 
	
	
	// ####################### Support Methods #############################
	
	private void clearReadLineBuffer() throws IOException
	{
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
	 * Runs the client
	 */
	public static void main(String[] args) throws Exception {
		ROVER_16  client = new ROVER_16 ();
		client.run();
	}
}