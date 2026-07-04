package com.example.s;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class UploadStudents {
    public static void upload(Context context) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();



        String[][] students = {

                {"Vaishnavi Hemantkumar Relekar","2265","vaishnavirelekar337@gmail.com","3rd"},
                {"Rutuja Sanjay Sangar","2233","rutujasangar13@gmail.com","3rd"},
                {"Kalyani Rajendra Kharmate","2230","kalyanikharmate777@gmail.com","3rd"},
                {"Shweta Niwas Suryavanshi","2248","shweta090208@gmail.com","3rd"},
                {"Shraddha Dhananjay Nalawade","2242","shraddhadn14@gmail.com","3rd"},
                {"Aniket Hanmant Zanje","2215","anikethzanje@gmail.com","3rd"},
                {"Yash Vinay Sutar","2213","yashvsutar07@gmail.com","3rd"},
                {"Sayali Jalindar Ubale","2243","ubalesayali1974@gmail.com","3rd"},
                {"Swayam Jitendra Kurkule","2251","swayamkurkule22@gmail.com","3rd"},
                {"Sayali Manik Suryawanshi","2235","suryawanshisayali812@gmail.com","3rd"},
                {"Kedar","2255","kedarborate234@gmail.com","3rd"},
                {"Shubham Rajaram Chitale","2267","shubhamchitale05@gmail.com","3rd"},
                {"Soham Anil Mhetre","2239","sohammhetre2828@gmail.com","3rd"},
                {"Ankita Yuvraj Pawar","2232","ankitapawar415116@gmail.com","3rd"},
                {"Sayali Sachin Yadav","2224","sayaliiyadav30@gmail.com","3rd"},
                {"Riddhik Vinod Kumbhar","2211","riddhikumbhar23@gmail.com","3rd"},
                {"RAVIKANT REVAPPA GODASE","2201","rravi934170@gmail.com","3rd"},
                {"Haridas Shahji Bhasad","2234","haridasbhasad@gmail.com","3rd"},
                {"Radhika Rahul Anugade","2257","radhikaanugade03@gmail.com","3rd"},
                {"Tanishka Nitin Muthekar","2223","jyotimuthekar@gmail.com","3rd"},
                {"Sanjana Prashant Katkar","2269","sanjanakatkar5552@gmail.com","3rd"},

                {"Mugdha Sudhir Katwate","0207","mugdhakatwate9108@gmail.com","1st"},
                {"Tejashri Baleshrao Thorat","0264","tejashrit6676@gmail.com","1st"},
                {"Gauri Sanjay Deshmukh","0231","deshmukhgauri816@gmail.com","1st"},
                {"Neel Surendra Desai","0222","desaineel1820@gmail.com","1st"},
                {"Shravani Samadhan Dhane","0248","shravani dhane2009@gmail.com","1st"},
                {"Vaishnavi Changdev Mane","0260","vaishnavicmane2009@gmail.com","1st"},
                {"Kasturi Sayaji Awale","0217","kasturiawale62@gmail.com","1st"},

                {"Sanika Sachin Tavare","1240","sanikatavare08@gmail.com","2nd"},
                {"Pranav Santosh Kadam","1239","kadampranav957@gmail.com","2nd"},
                {"Alisha Vasimraja Mujawar","1245","mujawar.alishaa@gmail.com","2nd"},
                {"Sarthak Sudam Koli","1269","sarthakkoli93@gmail.com","2nd"},
                {"Kendre Ganesh Gopinath","1265","gk1686931@gmail.com","2nd"},
                {"Mrunal Umesh Kale","1235","parikale1415@gmail.com","2nd"},
                {"Dnyaneshwari Vasant Bhosale","1211","bhosalednyaneshwari36@gmail.com","2nd"},
                {"Aditi Abhijeet Vasagadekar","1270","aditivasagadekar07@gmail.com","2nd"}

                // 👉 You can continue same format if needed
        };

        for (String[] s : students) {

            Map<String, Object> student = new HashMap<>();
            student.put("name", s[0]);
            student.put("rollNo", s[1]);
            student.put("department", "Computer Engineering");
            student.put("year", s[3]);
            student.put("classAssigned", false);
            student.put("faceRegistered", false);

            db.collection("students_master")
                    .document(s[2])
                    .set(student)
                    .addOnSuccessListener(aVoid ->
                            Log.d("FIREBASE", "Uploaded: " + s[0]))
                    .addOnFailureListener(e ->
                            Log.e("FIREBASE", "Error: ", e));
        }
    }
}