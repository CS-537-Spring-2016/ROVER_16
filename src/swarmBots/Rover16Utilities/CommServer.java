package swarmBots.Rover16Utilities;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

import java.net.HttpURLConnection;

public class CommServer {
	

		private static String strServer = "23.251.155.186:3000";
		private static String USER_AGENT = "Mozilla/5.0";

		public static boolean postData(CommObject commObject) throws Exception {
			boolean isSuccess = false;

			String urlServer = strServer;
			URL objServer = new URL("http://" + urlServer + "/api/global");
			System.out.println(objServer.toString());
			
			if (true) {
				HttpURLConnection conServer = (HttpURLConnection) objServer.openConnection();

				conServer.setRequestMethod("GET");

				//add request header
				conServer.setRequestProperty("User-Agent", USER_AGENT);

				int responseCode = conServer.getResponseCode();
				System.out.println("\nSending 'GET' request to URL : " + strServer);
				System.out.println("Response Code : " + responseCode);

				BufferedReader in = new BufferedReader(new InputStreamReader(conServer.getInputStream()));
				String inputLine;
				StringBuffer response = new StringBuffer();

				while ((inputLine = in.readLine()) != null) {
					response.append(inputLine);
				}
				in.close();

				//print result
				System.out.println(response.toString());

			}
			
			
			
			
			
			return isSuccess;
		}
}
