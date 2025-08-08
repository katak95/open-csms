package com.opencsms.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsms.core.tenant.TenantContext;
import com.opencsms.domain.station.ChargingStation;
import com.opencsms.service.station.StationService;
import com.opencsms.service.station.StationStatistics;
import com.opencsms.web.dto.ChargingStationDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for ChargingStationController.
 */
@WebMvcTest(ChargingStationController.class)
@ActiveProfiles("test")
public class ChargingStationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StationService stationService;

    private ChargingStation testStation;
    private ChargingStationDto testStationDto;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant("test-tenant");
        
        // Create test data
        testStation = new ChargingStation();
        testStation.setId(UUID.randomUUID());
        testStation.setStationId("TEST_STATION_001");
        testStation.setName("Test Station");
        testStation.setDescription("Test charging station");
        testStation.setAddress("123 Test Street");
        testStation.setCity("Test City");
        testStation.setCountry("US");
        testStation.setLatitude(BigDecimal.valueOf(40.7128));
        testStation.setLongitude(BigDecimal.valueOf(-74.0060));
        testStation.setMaxPowerKw(BigDecimal.valueOf(50.0));
        testStation.setNumConnectors(2);
        testStation.setStatus(ChargingStation.StationStatus.AVAILABLE);
        testStation.setActive(true);
        testStation.setDeleted(false);
        testStation.setOcppVersion("1.6");
        testStation.setHeartbeatInterval(300);
        testStation.setCreatedAt(Instant.now());
        testStation.setUpdatedAt(Instant.now());
        testStation.setVersion(1L);
        
        testStationDto = ChargingStationDto.fromEntity(testStation);
    }

    @Test
    void getAllStations_ShouldReturnStationsList() throws Exception {
        List<ChargingStation> stations = List.of(testStation);
        when(stationService.findAllActive()).thenReturn(stations);

        mockMvc.perform(get("/api/v1/stations")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].stationId").value("TEST_STATION_001"))
                .andExpect(jsonPath("$[0].name").value("Test Station"));

        verify(stationService).findAllActive();
    }

    @Test
    void getStationById_WhenExists_ShouldReturnStation() throws Exception {
        UUID stationId = testStation.getId();
        when(stationService.findById(stationId)).thenReturn(Optional.of(testStation));

        mockMvc.perform(get("/api/v1/stations/{id}", stationId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationId").value("TEST_STATION_001"))
                .andExpect(jsonPath("$.name").value("Test Station"));

        verify(stationService).findById(stationId);
    }

    @Test
    void getStationById_WhenNotExists_ShouldReturn404() throws Exception {
        UUID stationId = UUID.randomUUID();
        when(stationService.findById(stationId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/stations/{id}", stationId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(stationService).findById(stationId);
    }

    @Test
    void createStation_WithValidData_ShouldReturnCreatedStation() throws Exception {
        testStationDto.setId(null); // For creation
        when(stationService.createStation(any(ChargingStation.class))).thenReturn(testStation);

        mockMvc.perform(post("/api/v1/stations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testStationDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stationId").value("TEST_STATION_001"))
                .andExpect(jsonPath("$.name").value("Test Station"));

        verify(stationService).createStation(any(ChargingStation.class));
    }

    @Test
    void createStation_WithInvalidData_ShouldReturn400() throws Exception {
        ChargingStationDto invalidDto = new ChargingStationDto();
        // Missing required fields

        mockMvc.perform(post("/api/v1/stations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStation_WithValidData_ShouldReturnUpdatedStation() throws Exception {
        UUID stationId = testStation.getId();
        when(stationService.updateStation(eq(stationId), any(ChargingStation.class))).thenReturn(testStation);

        mockMvc.perform(put("/api/v1/stations/{id}", stationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testStationDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationId").value("TEST_STATION_001"))
                .andExpect(jsonPath("$.name").value("Test Station"));

        verify(stationService).updateStation(eq(stationId), any(ChargingStation.class));
    }

    @Test
    void deleteStation_WhenExists_ShouldReturn204() throws Exception {
        UUID stationId = testStation.getId();
        doNothing().when(stationService).deleteStation(stationId);

        mockMvc.perform(delete("/api/v1/stations/{id}", stationId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(stationService).deleteStation(stationId);
    }

    @Test
    void activateStation_ShouldReturn200() throws Exception {
        UUID stationId = testStation.getId();
        doNothing().when(stationService).activateStation(stationId);

        mockMvc.perform(post("/api/v1/stations/{id}/activate", stationId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(stationService).activateStation(stationId);
    }

    @Test
    void deactivateStation_ShouldReturn200() throws Exception {
        UUID stationId = testStation.getId();
        doNothing().when(stationService).deactivateStation(stationId);

        mockMvc.perform(post("/api/v1/stations/{id}/deactivate", stationId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(stationService).deactivateStation(stationId);
    }

    @Test
    void searchStations_WithQuery_ShouldReturnFilteredStations() throws Exception {
        List<ChargingStation> stations = List.of(testStation);
        when(stationService.searchStations("test")).thenReturn(stations);

        mockMvc.perform(get("/api/v1/stations/search")
                .param("query", "test")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));

        verify(stationService).searchStations("test");
    }

    @Test
    void searchStations_WithLocation_ShouldReturnNearbyStations() throws Exception {
        List<ChargingStation> stations = List.of(testStation);
        when(stationService.findNearbyStations(any(BigDecimal.class), any(BigDecimal.class), eq(10.0)))
            .thenReturn(stations);

        mockMvc.perform(get("/api/v1/stations/search")
                .param("latitude", "40.7128")
                .param("longitude", "-74.0060")
                .param("radiusKm", "10.0")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));

        verify(stationService).findNearbyStations(any(BigDecimal.class), any(BigDecimal.class), eq(10.0));
    }

    @Test
    void getStationStatistics_ShouldReturnStatistics() throws Exception {
        StationStatistics stats = StationStatistics.builder()
            .totalStations(5)
            .onlineStations(4)
            .availableStations(3)
            .occupiedStations(1)
            .faultedStations(0)
            .build();
        
        when(stationService.getStationStatistics()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/stations/statistics")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStations").value(5))
                .andExpect(jsonPath("$.onlineStations").value(4))
                .andExpect(jsonPath("$.availableStations").value(3));

        verify(stationService).getStationStatistics();
    }

    @Test
    void startMaintenance_ShouldReturn200() throws Exception {
        UUID stationId = testStation.getId();
        doNothing().when(stationService).enterMaintenanceMode(stationId, "Test maintenance");

        mockMvc.perform(post("/api/v1/stations/{id}/maintenance/start", stationId)
                .param("reason", "Test maintenance")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(stationService).enterMaintenanceMode(stationId, "Test maintenance");
    }

    @Test
    void endMaintenance_ShouldReturn200() throws Exception {
        UUID stationId = testStation.getId();
        doNothing().when(stationService).exitMaintenanceMode(stationId);

        mockMvc.perform(post("/api/v1/stations/{id}/maintenance/end", stationId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(stationService).exitMaintenanceMode(stationId);
    }
}