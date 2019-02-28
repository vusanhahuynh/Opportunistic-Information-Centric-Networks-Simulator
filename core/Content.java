/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package core;

/**
 *
 * @author vusan
 */
public class Content {

    int id;
    double timeOfCreation;
    double TTL;
    ContentType type;
    int size;
    double timeOfReceived;
    boolean isPublishedContent;

    public Content(int id, ContentType type, double timeOfCreation, double TTL, int size) {
        this.id = id;
        this.type = type;
        this.timeOfCreation = timeOfCreation;
        this.TTL = TTL;
        this.size = size;
        isPublishedContent = false;
    }

    public int getContentId() {
        return id;
    }

    public ContentType getContentType() {
        return type;
    }

    public void setContentType(ContentType type) {
        this.type = type;
    }

    public double getContentTimeOfCreation() {
        return timeOfCreation;
    }

    public double getContentTTL() {
        return TTL;
    }

    public int getSize() {
        return size;
    }

    public void setTimeOfReceived(double timeOfReceived) {
        this.timeOfReceived = timeOfReceived;
    }

    public double getTimeOfReceived() {
        return timeOfReceived;
    }

    public void setIsPublishedContent(boolean isPublishedContent) {
        this.isPublishedContent = isPublishedContent;
    }

    public boolean getIsPublishedContent() {
        return isPublishedContent;
    }

}
