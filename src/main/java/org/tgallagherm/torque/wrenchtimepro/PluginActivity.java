package org.tgallagherm.torque.wrenchtimepro;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class PluginActivity extends Activity {

    // Using a simple TextView to output the structured manufacturer details
    private TextView infoTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Ensure you have an activity_main.xml layout with a TextView id: vehicle_info_text
        setContentView(R.layout.activity_main);
        
        initializeViews();
        displayVehicleData();
    }

    /**
     * Initializes the UI components.
     */
    private void initializeViews() {
        infoTextView = findViewById(R.id.vehicle_info_text);
    }

    /**
     * Orchestrates gathering and rendering the data.
     */
    private void displayVehicleData() {
        List<Manufacturer> manufacturers = loadManufacturerSpecifications();
        String formattedOutput = formatManufacturerDetails(manufacturers);
        infoTextView.setText(formattedOutput);
    }

    /**
     * Data Layer: Generates the list of vehicle manufacturers.
     * Modular: Add new manufacturers or models by appending items to this list.
     */
    private List<Manufacturer> loadManufacturerSpecifications() {
        List<Manufacturer> list = new ArrayList<>();
        
        // Base technical details derived from standard manufacturer references
        list.add(new Manufacturer("Toyota", "Japan", "Global"));
        list.add(new Manufacturer("Ford", "USA", "Global"));
        list.add(new Manufacturer("General Motors", "USA", "Global"));
        
        return list;
    }

    /**
     * Presentation Layer: Transforms the data structures into clear, scannable text.
     * Modular: Adjust this function to change how information layout appears on screen.
     */
    private String formatManufacturerDetails(List<Manufacturer> manufacturers) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Vehicle Manufacturer Directory ===\n\n");
        
        for (Manufacturer maker : manufacturers) {
            sb.append("Brand: ").append(maker.getName()).append("\n")
              .append("Headquarters: ").append(maker.getHeadquarters()).append("\n")
              .append("Market Scope: ").append(maker.getMarketScope()).append("\n")
              .append("-----------------------------------\n");
        }
        
        return sb.toString();
    }

    /**
     * Inner Data Model representing basic Manufacturer metrics.
     * Modular: Expand fields here (e.g., specific torque specs, fastener lists) to grow features.
     */
    public static class Manufacturer {
        private final String name;
        private final String headquarters;
        private final String marketScope;

        public Manufacturer(String name, String headquarters, String marketScope) {
            this.name = name;
            this.headquarters = headquarters;
            this.marketScope = marketScope;
        }

        public String getName() { return name; }
        public String getHeadquarters() { return headquarters; }
        public String getMarketScope() { return marketScope; }
    }
}