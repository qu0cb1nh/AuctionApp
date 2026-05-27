package net.auctionapp.common.messages.auction;

import net.auctionapp.common.dto.ActivitySummaryDto;
import net.auctionapp.common.messages.Message;
import net.auctionapp.common.messages.MessageType;

import java.util.List;

public class MyActivityResponseMessage extends Message {
    private List<ActivitySummaryDto> activities;

    public MyActivityResponseMessage() {
        super(MessageType.MY_ACTIVITY_RESPONSE);
    }

    public MyActivityResponseMessage(List<ActivitySummaryDto> activities) {
        super(MessageType.MY_ACTIVITY_RESPONSE);
        this.activities = activities == null ? List.of() : List.copyOf(activities);
    }

    public List<ActivitySummaryDto> getActivities() {
        return activities == null ? List.of() : List.copyOf(activities);
    }
}
