package controllers;

import java.io.IOException;
import java.io.Serial;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import beans.Auction;
import beans.User;
import dao.AuctionDAO;
import utilities.ConnectionHandler;
import utilities.LocalDateTimeTypeAdapter;

@WebServlet("/OpenAuctionsListRIA")
public class OpenAuctionsListRIA extends HttpServlet {
	@Serial
	private static final long serialVersionUID = 1L;
	private Connection connection = null;

	public OpenAuctionsListRIA() {
		super();
	}

	/**
	 * Initializes the configuration of the servlet, of the thymeleaf engine and
	 * connects to the database
	 */
	public void init() throws ServletException {
		ServletContext context = getServletContext();
		connection = ConnectionHandler.getConnection(context);
	}

	/**
	 * Checks if the connection is active
	 */
	private boolean checkConnection(Connection connection) {
		try {
			if (connection != null && !connection.isClosed())
				return true;
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return false;
	}

	private String getRemainingTime(LocalDateTime from, LocalDateTime to) {
		long diffDays = ChronoUnit.DAYS.between(from, to);
		long diffHours = ChronoUnit.HOURS.between(from, to);
		long hoursBetween = diffHours - (diffDays * 24);

		return diffDays + " days and " + hoursBetween + " hours";
	}

	private void setupPage(HttpServletRequest request, HttpServletResponse response) throws IOException {
		List<Auction> openAuctions;
		User user = (User) request.getSession(false).getAttribute("user");
		int userID = user.getUserID();
		LocalDateTime loginTime = (LocalDateTime) request.getSession(false).getAttribute("loginTime");

		// checks if the connection is active
		if (checkConnection(connection)) {
			AuctionDAO auc = new AuctionDAO(connection);

			try {
				// retrieves all user's open auctions ordered by deadline ascending
				openAuctions = auc.getOpenAuctionsByUser(userID);
				
				if (openAuctions != null && !openAuctions.isEmpty()) {
					for (Auction a : openAuctions) {
						String remainingTime = getRemainingTime(loginTime, a.getExpiryDate());
						a.setRemainingTime(remainingTime);
					}
				} else {
					openAuctions = null;
				}
			} catch (SQLException e) {
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				response.getWriter().println("Errore: accesso al database fallito!");
				return;
			}

			Gson gson = new GsonBuilder().registerTypeAdapter(LocalDateTime.class, new LocalDateTimeTypeAdapter())
					.create();
			String json = gson.toJson(openAuctions);

			response.setStatus(HttpServletResponse.SC_OK);
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			response.getWriter().write(json);
		}
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		// checks if the session does not exist or is expired
		if (request.getSession(false) == null || request.getSession(false).getAttribute("user") == null) {
			String loginPath = getServletContext().getContextPath() + "/index.html";
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.setHeader("Location", loginPath);
		} else {
			setupPage(request, response);
		}
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		doGet(request, response);
	}

	/**
	 * Called when the servlet is destroyed
	 */
	public void destroy() {
		try {
			ConnectionHandler.closeConnection(connection);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
