package com.senarios.checksandcards;

public class LeadRect
{
    private int left;
    private int top;
    private int right;
    private int bottom;
    private int _x;
    private int _y;
    private int _width;
    private int _height;

    public int get_x()
    {
        return _x;
    }

    public void set_x(int _x)
    {
        this._x = _x;
    }

    public int get_y()
    {
        return _y;
    }

    public void set_y(int _y)
    {
        this._y = _y;
    }

    public int get_width()
    {
        return _width;
    }

    public void set_width(int _width)
    {
        this._width = _width;
    }

    public int get_height()
    {
        return _height;
    }

    public void set_height(int _height)
    {
        this._height = _height;
    }

    public LeadRect(int left, int top, int width, int height)
    {
        this.left = left;
        this.top = top;
        this.right = left + width;
        this.bottom = top + height;
        this.update();
    }

    public static LeadRect fromLTRB(int left, int top, int right, int bottom) {
        return new LeadRect(left, top, right - left, bottom - top);
    }

    private void update()
    {
        this._x = this.left;
        this._y = this.top;
        this._width = this.right - this.left;
        this._height = this.bottom - this.top;
    }

    public int getLeft()
    {
        return left;
    }

    public void setLeft(int left)
    {
        this.left = left;
    }

    public int getTop()
    {
        return top;
    }

    public void setTop(int top)
    {
        this.top = top;
    }

    public int getRight()
    {
        return right;
    }

    public void setRight(int right)
    {
        this.right = right;
    }

    public int getBottom()
    {
        return bottom;
    }

    public void setBottom(int bottom)
    {
        this.bottom = bottom;
    }
}
