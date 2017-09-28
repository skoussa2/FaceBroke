package facebroke;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import facebroke.model.User;
import facebroke.util.FacebrokeException;
import facebroke.util.HibernateUtility;
import facebroke.util.ValidationSnipets;


@WebServlet("/settings")
public class Settings extends HttpServlet {
	private static Logger log = LoggerFactory.getLogger(Settings.class);
	private static final long serialVersionUID = 1L;

    public Settings() {
        super();
    }


	protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		log.info("Got "+req.getParameterMap().size()+" paramters to GET");
		
		if (!ValidationSnipets.isValidSession(req.getSession())) {
			res.sendRedirect("index.jsp");
			log.info("Failed to validate session with \"valid\"=" + req.getSession().getAttribute("valid"));
			return;
		}
		
		String target_id_string = req.getParameter("id");
		
		log.info("Received GET param \"id\"="+target_id_string);
		
		// Validate permissions
		try {
			Session sess = HibernateUtility.getSessionFactory().openSession();
			long target_id = Long.parseLong(target_id_string);
			List<User> target_list = (List<User>) sess.createQuery("FROM User u where u.id = :user_id")
									.setParameter("user_id", target_id)
									.list();
			List<User> current_user_list = (List<User>) sess.createQuery("FROM User u where u.id = :user_id")
											.setParameter("user_id", (long)req.getSession().getAttribute("user_id"))
											.list();
			
			if(target_list == null || target_list.isEmpty() || current_user_list == null || current_user_list.isEmpty()) {
				throw new FacebrokeException("User with id = \""+target_id+"\" is not currently accessible");
			}
			User target = target_list.get(0);
			User current_user = current_user_list.get(0);
			
			if(target == null) {
				sess.close();
				throw new FacebrokeException("Invalid user id = \""+target.getId()+"\" for Settings page");
			}
			
			if(target.getId() != current_user.getId() && !current_user.getRole().equals(User.UserRole.ADMIN)) {
				sess.close();
				throw new FacebrokeException("User with id = \""+current_user.getId()+"\" has insufficient privileges to lookup other users' settings");
			}
			
			
			// If we made it here, the current user is either an admin or owner of the account specified
			req.getSession().setAttribute("target", target);
			req.setAttribute("target_user_id", target.getId());
			req.getRequestDispatcher("settings.jsp").forward(req, res);
			sess.close();
			
		}catch(FacebrokeException e) {
			req.setAttribute("serverMessage", e.getMessage());
			req.getRequestDispatcher("error.jsp").forward(req, res);
			return;
		}
	}

	protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		log.info("Got "+req.getParameterMap().size()+" paramters to POST");
		if (!ValidationSnipets.isValidSession(req.getSession())) {
			res.sendRedirect("index.jsp");
			log.info("Failed to validate session with \"valid\"=" + req.getSession().getAttribute("valid"));
			return;
		}
		
		RequestDispatcher reqDis = req.getRequestDispatcher("settings");
		
		String target_id_string = req.getParameter("target_id");
		String username = req.getParameter("regUsername");
		String email = req.getParameter("regEmail");
		String fname = req.getParameter("regFirstName");
		String lname = req.getParameter("regLastName");
		String dob_raw = req.getParameter("regDOB");
		String pass1 = req.getParameter("regPassword");
		String pass2 = req.getParameter("regPasswordConfirm");
		Date dob;
		
		
		// Get a session to fetch the target user to be updated
		long target_id = Long.parseLong(target_id_string);
		Session sess = HibernateUtility.getSessionFactory().openSession();
		List<User> target_list = (List<User>) sess.createQuery("FROM User u where u.id = :user_id")
								.setParameter("user_id", target_id)
								.list();
		
		try {
			if(target_list == null || target_list.isEmpty()) {
				throw new FacebrokeException("User with id = \""+target_id+"\" is not currently accessible");
			}
			
			User target = target_list.get(0);
			List<String> errors = new ArrayList<>();
			List<String> changes = new ArrayList<>();
			
			
			
			// Change the user name if needed
			if (username != null && !target.getUsername().equals(username)) {
				if(!ValidationSnipets.isUsernameTaken(username)) {
					target.setUsername(username);
					changes.add("Username updated");
				}else {
					errors.add("Username is unavaialble");
				}
			}
			
			
			// Validate the email
			if(email == null || !target.getEmail().equals(email)) {
				if(!ValidationSnipets.isEmailTaken(email)) {
					target.setEmail(email);
					changes.add("Email is updated");
				}else {
					errors.add("Email is unavaialble");
				}
			}
			
			/*
			
			// Validate first and last names
			if (fname == null || fname.length() < 1) {
				req.setAttribute("authMessage", "First name can't be blank. If you have a mononym, leave Last Name blank");
				reqDis.forward(req, res);
				return;
			}
			
			if(lname == null) {
				lname = "";
			}
			
			
			// Validate DOB
			if (dob_raw == null) {
				req.setAttribute("authMessage", "Date of Bith can't be blank");
				reqDis.forward(req, res);
				return;
			}
			try {
				dob = ValidationSnipets.parseDate(dob_raw);
			} catch (ParseException e) {
				req.setAttribute("authMessage", "Invalid Date of Birth Format. Need yyyy-mm-dd");
				reqDis.forward(req, res);
				return;
			}
			
			
			// Validate Password
			if (pass1 == null || pass1.length() < 1 || pass2 ==null || pass2.length() < 1) {
				req.setAttribute("authMessage", "Password can't be blank");
				reqDis.forward(req, res);
				return;
			}
			
			if (!ValidationSnipets.passwordFormatValid(pass1)) {
				req.setAttribute("authMessage", "Password must be at least 8 characters long and contain only a-z,A-z,0-9,!,#,$,^");
				reqDis.forward(req, res);
				return;
			}
			
			if (!pass1.equals(pass2)) {
				req.setAttribute("authMessage", "Passwords don't match");
				reqDis.forward(req, res);
				return;
			}
			
			*/
		}catch(FacebrokeException e) {
			req.setAttribute("serverMessage", e.getMessage());
			req.getRequestDispatcher("error.jsp").forward(req, res);
			return;
		}
		
	}

}
