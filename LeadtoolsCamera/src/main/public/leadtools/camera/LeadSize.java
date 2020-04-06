package leadtools.camera;

public class LeadSize
{
    private int measuredWidth;
    private int measuredHeight;
    private byte[] jpegData;
    private int width;
    private int height;

    public LeadSize(int measuredWidth, int measuredHeight, byte[] jpegData, int width, int height)
    {
        this.measuredWidth = measuredWidth;
        this.measuredHeight = measuredHeight;
        this.jpegData = jpegData;
        this.width = width;
        this.height = height;
    }

    public int getMeasuredWidth()
    {
        return measuredWidth;
    }

    public void setMeasuredWidth(int measuredWidth)
    {
        this.measuredWidth = measuredWidth;
    }

    public int getMeasuredHeight()
    {
        return measuredHeight;
    }

    public void setMeasuredHeight(int measuredHeight)
    {
        this.measuredHeight = measuredHeight;
    }

    public byte[] getJpegData()
    {
        return jpegData;
    }

    public void setJpegData(byte[] jpegData)
    {
        this.jpegData = jpegData;
    }

    public int getWidth()
    {
        return width;
    }

    public void setWidth(int width)
    {
        this.width = width;
    }

    public int getHeight()
    {
        return height;
    }

    public void setHeight(int height)
    {
        this.height = height;
    }
}
