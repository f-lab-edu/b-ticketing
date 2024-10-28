package com.bticketing.main;

import com.bticketing.main.controller.SeatSelectionController;
import com.bticketing.main.dto.SeatSelectionDto;
import com.bticketing.main.enums.UserType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SeatSelectionController.class)
public class SeatSelectionControllerMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testAutoAssignSeats() throws Exception {
        // given
        SeatSelectionDto requestDto = new SeatSelectionDto(1,2,3, null);
        String jsonContent = objectMapper.writeValueAsString(requestDto);

        // when & then
        mockMvc.perform(post("/seats/auto-assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent))
                .andExpect(status().isOk())
                .andExpect(content().string("자동 좌석 선택 완료: 3개 좌석 배정"));
    }

    @Test
    public void testSelectSeats() throws Exception {
        // given
        SeatSelectionDto requestDto = new SeatSelectionDto(1, 2, 3, new int[]{101, 102, 103});
        String jsonContent = objectMapper.writeValueAsString(requestDto);

        // when & then
        mockMvc.perform(post("/seats/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent))
                .andExpect(status().isOk())
                .andExpect(content().string("수동 좌석 선택 완료: 좌석 ID [101, 102, 103]"));
    }

    @Test
    public void testCheckVIPAccess() throws Exception {
        // given
        UserType userType = UserType.VIP;

        // when & then
        mockMvc.perform(get("/seats/vip-access")
                        .param("userType", userType.name()))
                .andExpect(status().isOk())
                .andExpect(content().string("선예매 유저입니다."));
    }
}
