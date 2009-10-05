package com.nethackff;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;

import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.Thread;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class TerminalView extends View {
	String outputText;

	private char[] textBuffer;
	private char[] fmtBuffer;
	int numRows;
	int numColumns;

	int currentRow;
	int currentColumn;

	int changeColumn1, changeColumn2;
	int changeRow1, changeRow2;

	Paint textPaint;

	protected void clearChange() {
		changeColumn1 = numColumns;
		changeColumn2 = -1;
		changeRow1 = numRows;
		changeRow2 = -1;
	}

	protected void registerChange(int column, int row) {
		if (column < changeColumn1) {
			changeColumn1 = column;
		}
		if (column > changeColumn2) {
			changeColumn2 = column;
		}
		if (row < changeRow1) {
			changeRow1 = row;
		}
		if (row > changeRow2) {
			changeRow2 = row;
		}
	}

	protected void onMeasure(int widthmeasurespec, int heightmeasurespec) {
		int minheight = getSuggestedMinimumHeight();
		int minwidth = getSuggestedMinimumWidth();

		// TODO: Prevent duplication
		Paint paint = new Paint();
		paint.setTypeface(Typeface.MONOSPACE);
		paint.setTextSize(10);
		paint.setAntiAlias(true);
		int charheight = (int) Math.ceil(paint.getFontSpacing());// +
		// paint.ascent());
		int charwidth = (int) paint.measureText("X", 0, 1);

		int width, height;
		width = numColumns * charwidth;
		height = numRows * charheight;

		height += 2; // MAGIC!

		if (width < minwidth) {
			width = minwidth;
		}
		if (height < minheight) {
			height = minheight;
		}

		int modex = MeasureSpec.getMode(widthmeasurespec);
		int modey = MeasureSpec.getMode(heightmeasurespec);
		if (modex == MeasureSpec.AT_MOST) {
			width = Math.min(MeasureSpec.getSize(widthmeasurespec), width);
		} else if (modex == MeasureSpec.EXACTLY) {
			width = MeasureSpec.getSize(widthmeasurespec);
		}
		if (modey == MeasureSpec.AT_MOST) {
			height = Math.min(MeasureSpec.getSize(heightmeasurespec), height);
		} else if (modey == MeasureSpec.EXACTLY) {
			height = MeasureSpec.getSize(heightmeasurespec);
		}
		setMeasuredDimension(width, height);
	}

	public static final int kColBlack = 0;
	public static final int kColRed = 1;
	public static final int kColGreen = 2;
	public static final int kColYellow = 3;
	public static final int kColBlue = 4;
	public static final int kColMagenta = 5;
	public static final int kColCyan = 6;
	public static final int kColWhite = 7;

	int colorForeground = kColWhite, colorBackground = kColBlack;

	char encodeFormat(int foreground, int background, boolean reverse,
			boolean bright, boolean underline) {
		if (reverse) {
			foreground = 7 - foreground;
			background = 7 - background;
		}
		if (bright) {
			foreground += 8;
		}
		if (underline) {
			foreground += 16;
		}
		return (char) ((foreground << 3) + background);
	}

	int decodeFormatForeground(char fmt) {
		return (fmt >> 3) & 31;
	}

	int decodeFormatBackground(char fmt) {
		return fmt & 7;
	}

	char encodeCurrentFormat() {
		return encodeFormat(colorForeground, colorBackground, grReverseVideo,
				grBright, grUnderline);
	}

	void clearScreen() {
		for (int i = 0; i < numRows * numColumns; i++) {
			textBuffer[i] = ' ';
			fmtBuffer[i] = encodeCurrentFormat();
		}
	}

	void clampCursorPos() {
		if (currentRow < 0) {
			currentRow = 0;
		} else if (currentRow >= numRows) {
			// Should we scroll down in this case?
			currentRow = numRows - 1;
		}
		if (currentColumn < 0) {
			currentColumn = 0;
		} else if (currentColumn >= numColumns) {
			currentColumn = numColumns - 1;
		}
	}

	void moveCursorRel(int coldelta, int rowdelta) {
		currentRow += rowdelta;
		currentColumn += coldelta;
		clampCursorPos();
	}

	void moveCursorAbs(int newcol, int newrow) {
		currentRow = newrow;
		currentColumn = newcol;
		clampCursorPos();
	}

	public void lineFeed() {
		currentRow++;
		currentColumn = 0;

		if (currentRow >= numRows) {
			for (int row = 1; row < numRows; row++) {
				for (int col = 0; col < numColumns; col++) {
					textBuffer[(row - 1) * numColumns + col] = textBuffer[row
							* numColumns + col];
					fmtBuffer[(row - 1) * numColumns + col] = fmtBuffer[row
							* numColumns + col];
				}
			}
			for (int col = 0; col < numColumns; col++) {
				textBuffer[(numRows - 1) * numColumns + col] = ' ';
				fmtBuffer[(numRows - 1) * numColumns + col] = encodeCurrentFormat();
			}
			currentRow--;

			changeColumn1 = 0;
			changeColumn2 = numColumns - 1;
			changeRow1 = 0;
			changeRow2 = numRows - 1;
		}

	}

	public void writeRaw(char c) {
		if (currentColumn >= numColumns) {
			lineFeed();
		}

		if (currentColumn < numColumns && currentRow < numRows) {
			textBuffer[currentRow * numColumns + currentColumn] = c;
			fmtBuffer[currentRow * numColumns + currentColumn] = encodeCurrentFormat();

			registerChange(currentColumn, currentRow);
		}
		currentColumn++;
	}

	public void setCharAtPos(char c, int col, int row) {
		if (col >= 0 && col < numColumns && row >= 0 && row < numRows) {
			textBuffer[row * numColumns + col] = c;
			fmtBuffer[row * numColumns + col] = encodeCurrentFormat();
			registerChange(col, row);
		}
	}

	public void writeRawStr(String s) {
		int len = s.length();
		for (int i = 0; i < len; i++) {
			writeRaw(s.charAt(i));
		}
	}

	private static final int ESC_NONE = 0;
	private static final int ESC = 1;
	private static final int ESC_LEFT_SQUARE_BRACKET = 2;

	private int escapeState;

	public void startEscapeSequence(int state) {
		escSeqLen = 0;
		escapeState = state;
	}

	public void updateEscapeSequence(char c) {
		if (escSeqLen < kMaxEscSeqLen) {
			escSeqStored[escSeqLen++] = c;
		}
		switch (escapeState) {
		case ESC:
			updateEscapeSequenceEsc(c);
			break;

		case ESC_LEFT_SQUARE_BRACKET:
			updateEscapeSequenceLeftSquareBracket(c);
			break;

		default:
			reportUnknownSequence();
			escapeState = ESC_NONE;
			break;
		}
	}

	public void updateEscapeSequenceEsc(char c) {
		switch (c) {
		case '[':
			escapeState = ESC_LEFT_SQUARE_BRACKET;
			escSeqArgVal[0] = 0;
			escSeqArgCnt = -1;
			break;

		default:
			reportUnknownSequence();
			escapeState = ESC_NONE;
			break;
		}
	}

	public static final int kMaxEscParam = 16;
	public int[] escSeqArgVal = new int[kMaxEscParam];
	public int escSeqArgCnt = 0;

	public static final int kMaxEscSeqLen = 64; // Not sure...
	public char[] escSeqStored = new char[kMaxEscSeqLen];
	public int escSeqLen = 0;

	public int getEscSeqArgVal(int deflt) {
		if (escSeqArgCnt < 0) {
			// No arguments specified.
			return deflt;
		} else {
			return escSeqArgVal[escSeqArgCnt];
		}
	}

	public void reportUnknownChar(char c) {
		if (currentColumn > 1) {
			lineFeed();
		}
		writeRawStr("Unknown character: " + (int) c);
		lineFeed();
	}

	public void reportUnknownSequence() {
		if (currentColumn > 1) {
			lineFeed();
		}
		writeRawStr("Unknown Esc sequence: ");
		for (int i = 0; i < escSeqLen; i++) {
			writeRaw(escSeqStored[i]);
		}
		lineFeed();
	}

	public boolean grReverseVideo = false;
	public boolean grBright = false;
	public boolean grUnderline = false;

	public void selectGraphicRendition(int arg) {
		if (arg >= 30 && arg <= 37) {
			colorForeground = arg - 30;
			return;
		}
		if (arg >= 40 && arg <= 47) {
			colorBackground = arg - 40;
			return;
		}
		switch (arg) {
		case 0:
			grReverseVideo = false;
			colorForeground = kColWhite;
			colorBackground = kColBlack;
			grBright = false; // Not sure
			grUnderline = false;
			break;
		case 1:
			grBright = true;
			break;
		case 2:
			grBright = false;
			break;
		case 3:
			reportUnknownSequence();
			break;
		case 4:
			grUnderline = true;
			break;
		case 5:
		case 6:
			reportUnknownSequence();
			break;
		case 7:
			grReverseVideo = true;
			break;
		default:
			reportUnknownSequence();
			break;
		}
	}

	public void selectGraphicRendition() {
		if (escSeqArgCnt < 0) {
			selectGraphicRendition(0);
		} else {
			for (int i = 0; i <= escSeqArgCnt; i++) {
				selectGraphicRendition(escSeqArgVal[i]);
			}
		}
	}

	public void updateEscapeSequenceLeftSquareBracket(char c) {
		switch (c) {
		case 'B': // Move cursor down n lines
			moveCursorRel(0, getEscSeqArgVal(1));
			escapeState = ESC_NONE;
			return;
		case 'C': // Move cursor right n lines
			moveCursorRel(getEscSeqArgVal(1), 0);
			escapeState = ESC_NONE;
			return;
		case 'D': // Move cursor left n lines
			moveCursorRel(-getEscSeqArgVal(1), 0);
			escapeState = ESC_NONE;
			return;
		case 'A': // Move cursor up n lines
			moveCursorRel(0, -getEscSeqArgVal(1));
			escapeState = ESC_NONE;
			return;
		case 'H': // Cursor home
			if (escSeqArgCnt == 1) {
				moveCursorAbs(escSeqArgVal[1] - 1, escSeqArgVal[0] - 1);
			} else {
				moveCursorAbs(0, 0);
			}
			escapeState = ESC_NONE;
			return;
		case 'J': // Clear screen
			// TODO: Read arguments here.
			clearScreen();
			escapeState = ESC_NONE;
			return;
		case 'K':
			if (getEscSeqArgVal(0) == 0) {
				// Clear line from cursor right
				for (int i = currentColumn; i < numColumns; i++) {
					setCharAtPos(' ', i, currentRow);
				}
			} else if (getEscSeqArgVal(0) == 1) {
				// Clear line from cursor left
				for (int i = currentColumn; i >= 0; i--) {
					setCharAtPos(' ', i, currentRow);
				}
			} else if (getEscSeqArgVal(0) == 2) {
				for (int i = 0; i < numColumns; i++) {
					setCharAtPos(' ', i, currentRow);
				}
			} else {
				reportUnknownSequence();
			}
			escapeState = ESC_NONE;
			return;
		case 'm': // Select graphic rendition
			selectGraphicRendition();
			escapeState = ESC_NONE;
			return;
		}
		if (c >= '0' && c <= '9') {
			if (escSeqArgCnt == -1) {
				escSeqArgCnt = 0;
			}
			escSeqArgVal[escSeqArgCnt] = escSeqArgVal[escSeqArgCnt] * 10
					+ (c - '0');
		} else if (c == ';') {
			escSeqArgCnt++;
			escSeqArgVal[escSeqArgCnt] = 0;
		} else {
			reportUnknownSequence();
			escapeState = ESC_NONE;
		}
	}

	public void write(char c) {
		switch (c) {
		case 0: // NUL
			break;
		case 7: // BEL
			break;
		case 8: // BS
			if (currentColumn > 0)
				currentColumn--;
			break;
		case 9: // HT
			// TODO
			reportUnknownChar(c);
			break;
		case 13:
			currentColumn = 0;
			return;
		case 10: // CR
		case 11: // VT
		case 12: // LF
			lineFeed();
			return;
		case 14: // SO
			// TODO
			break;
		case 15: // SI
			// TODO
			break;
		case 24: // CAN
		case 26: // SUB
			// TODO
			// break;
		case 0x9b: // CSI
			reportUnknownChar(c);
			break;
		case 27: // ESC
			startEscapeSequence(ESC);
			return;
		};

		if(escapeState == ESC_NONE)
		{
			if(c >= 32)
			{
				writeRaw(c);
			}
		}
		else
		{
			updateEscapeSequence(c);
		}
	}

	public void write(String s) {
		int len = s.length();
		for (int i = 0; i < len; i++) {
			write(s.charAt(i));
		}
	}

	public TerminalView(Context context, int columns, int rows) {
		super(context);

		outputText = "";

		numRows = rows;
		numColumns = columns;

		textBuffer = new char[rows * columns];
		fmtBuffer = new char[rows * columns];

		// Paint paint = createPaint();
		textPaint = new Paint();
		textPaint.setTypeface(Typeface.MONOSPACE);
		textPaint.setTextSize(10);
		textPaint.setAntiAlias(true);

		charHeight = (int) Math.ceil(textPaint.getFontSpacing());
		charWidth = (int) textPaint.measureText("X", 0, 1);

		clearScreen();

		currentRow = 0;
		currentColumn = 0;
	}

	public String getContents() {
		String r = "";
		for (int i = 0; i < numRows; i++) {
			r += getRow(i);
			r += '\n';
		}
		return r;
	}

	public String getRow(int row) {
		String r;
		int offs = row * numColumns;
		r = "";
		for (int i = 0; i < numColumns; i++) {
			r += textBuffer[offs + i];
		}
		return r;
	}

	void setPaintColorForeground(Paint paint, int col) {
		if ((col & 8) != 0) {
			paint.setFakeBoldText(true);
			col &= ~8;
		} else {
			paint.setFakeBoldText(false);
		}
		if ((col & 16) != 0) {
			paint.setUnderlineText(true);
			col &= ~16;
		} else {
			paint.setUnderlineText(false);
		}
		switch (col) {
		case kColBlack:
			paint.setARGB(0xff, 0x00, 0x00, 0x00);
			break;
		case kColRed:
			paint.setARGB(0xff, 0xff, 0x00, 0x00);
			break;
		case kColGreen:
			paint.setARGB(0xff, 0x00, 0xff, 0x00);
			break;
		case kColYellow:
			paint.setARGB(0xff, 0xff, 0xff, 0x00);
			break;
		case kColBlue:
			paint.setARGB(0xff, 0x00, 0x00, 0xff);
			break;
		case kColMagenta:
			paint.setARGB(0xff, 0xff, 0x00, 0xff);
			break;
		case kColCyan:
			paint.setARGB(0xff, 0x00, 0xff, 0xff);
			break;
		case kColWhite:
			paint.setARGB(0xff, 0xff, 0xff, 0xff);
			break;
		default:
			paint.setARGB(0x80, 0x80, 0x80, 0x80);
			break;
		}
	}

	void setPaintColorBackground(Paint paint, int col) {
		switch (col) {
		case kColBlack:
			paint.setARGB(0xff, 0x00, 0x00, 0x00);
			break;
		case kColRed:
			paint.setARGB(0xff, 0xff, 0x00, 0x00);
			break;
		case kColGreen:
			paint.setARGB(0xff, 0x00, 0xff, 0x00);
			break;
		case kColYellow:
			paint.setARGB(0xff, 0xff, 0xff, 0x00);
			break;
		case kColBlue:
			paint.setARGB(0xff, 0x00, 0x00, 0xff);
			break;
		case kColMagenta:
			paint.setARGB(0xff, 0xff, 0x00, 0xff);
			break;
		case kColCyan:
			paint.setARGB(0xff, 0x00, 0xff, 0xff);
			break;
		case kColWhite:
			paint.setARGB(0xff, 0xff, 0xff, 0xff);
			break;
		default:
			paint.setARGB(0x80, 0x80, 0x80, 0x80);
			break;
		}
	}

	// TODO
	int charHeight = 0;
	int charWidth = 0;

	int computeCoordX(int column) {
		return charWidth * column;
	}

	int computeCoordY(int row) {
		int ybackgroffs = 0;
		return row * charHeight + ybackgroffs;
	}

	int computeColumnFromCoordX(int coordx) {
		return coordx / charWidth;
	}

	int computeRowFromCoordY(int coordy) {
		int ybackgroffs = 0;
		return (coordy - ybackgroffs) / charHeight;
	}

	/*
	 * Paint createPaint() { Paint paint = new Paint();
	 * paint.setTypeface(Typeface.MONOSPACE); paint.setTextSize(10);
	 * paint.setAntiAlias(true); return paint; }
	 */
	protected void onDraw(Canvas canvas) {
		/*
		 * Paint paint = createPaint(); if(charHeight == 0) { charHeight =
		 * (int)Math.ceil(paint.getFontSpacing());// + paint.ascent());
		 * charWidth = (int)paint.measureText("X", 0, 1); }
		 */
		// char tmp[] = {' ', ' '};
		int x, y;

		int row1 = 0;
		int row2 = numRows;
		int col1 = 0;
		int col2 = numColumns;

		Rect cliprect = new Rect();
		if (canvas.getClipBounds(cliprect)) {
			col1 = Math.max(computeColumnFromCoordX(cliprect.left), 0);
			col2 = Math.min(computeColumnFromCoordX(cliprect.right + charWidth
					- 1), numColumns);
			row1 = Math.max(computeRowFromCoordY(cliprect.top), 0);
			row2 = Math.min(computeRowFromCoordY(cliprect.bottom + charHeight
					- 1), numRows);
		}

		x = 0;
		y = computeCoordY(row1);
		for (int row = row1; row < row2; row++) {
			x = computeCoordX(col1);
			int currentx1 = -1;
			int currentcolor = -1;
			for (int col = col1; col < col2; col++, x += charWidth) {
				char fmt = fmtBuffer[row * numColumns + col];
				int color = decodeFormatBackground(fmt);
				if (color == currentcolor) {
					continue;
				}
				if (currentx1 >= 0) {
					setPaintColorBackground(textPaint, currentcolor);
					canvas.drawRect(currentx1, y, x, y + charHeight, textPaint);
				}
				currentx1 = x;
				currentcolor = color;
			}
			setPaintColorBackground(textPaint, currentcolor);
			canvas.drawRect(currentx1, y, x, y + charHeight, textPaint);
			y += charHeight;
		}

		x = 0;

		int ybackgroffs = 3;
		y = charHeight + computeCoordY(row1) - ybackgroffs;
		for (int row = row1; row < row2; row++) {
			x = computeCoordX(col1);
			int currentx1 = -1;
			int currentcolor = -1;
			String currentstr = "";
			for (int col = col1; col < col2; col++, x += charWidth) {
				char fmt = fmtBuffer[row * numColumns + col];
				int color = decodeFormatForeground(fmt);
				char c = textBuffer[row * numColumns + col];
				if (color == currentcolor) {
					currentstr += c;
					continue;
				}
				if (currentx1 >= 0) {
					setPaintColorForeground(textPaint, currentcolor);
					canvas.drawText(currentstr, 0, currentstr.length(),
							(float) currentx1, (float) y, textPaint);
				}
				currentx1 = x;
				currentcolor = color;
				currentstr = "" + c;
			}
			setPaintColorForeground(textPaint, currentcolor);
			canvas.drawText(currentstr, 0, currentstr.length(),
					(float) currentx1, (float) y, textPaint);
			y += charHeight;
		}

		clearChange();
	}
}

public class NetHackApp extends Activity implements Runnable {
	TerminalView screen;

	/* For debugging only. */
	TerminalView dbgTerminalTranscript;

	public boolean altKeyDown = false;
	public boolean shiftKeyDown = false;
	public boolean ctrlKeyDown = false;
	
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if(keyCode == KeyEvent.KEYCODE_ALT_LEFT
				|| keyCode == KeyEvent.KEYCODE_ALT_RIGHT)
		{
			altKeyDown = true;
			return true;
		}
		if(keyCode == KeyEvent.KEYCODE_SHIFT_LEFT
				|| keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT)
		{
			shiftKeyDown = true;
			return true;
		}

		// There is no Ctrl key on the G1 keyboard, and we can't use
		// Shift or Alt for it, as they are needed for other things.
		// The search button was the best remaining alternative
		// (the menu button could work too, but may want to use that
		// for an actual application menu instead).
		if(keyCode == KeyEvent.KEYCODE_SEARCH)
		{
			ctrlKeyDown = true;
			return true;
		}
		String s = "";

		char c = (char) event
				.getUnicodeChar((shiftKeyDown ? KeyEvent.META_SHIFT_ON : 0)
						| (altKeyDown ? KeyEvent.META_ALT_ON : 0));
		if(ctrlKeyDown)
		{
			// This appears to be how the ASCII numbers would have been
			// represented if we had a Ctrl key, so now we apply that
			// for the search key instead. This is for commands like kick
			// (^D).
			c = (char)(((int)c) & 0x1f);
		}

		// Map the delete button to backspace.
		if(keyCode == KeyEvent.KEYCODE_DEL)
		{
			c = 8;
		}
		
		if (c != 0)
		{
			s += c;
			TerminalSend(s);
		}

		return true;
	}

	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if(keyCode == KeyEvent.KEYCODE_ALT_LEFT
				|| keyCode == KeyEvent.KEYCODE_ALT_RIGHT)
		{
			altKeyDown = false;
		}
		if(keyCode == KeyEvent.KEYCODE_SHIFT_LEFT
				|| keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT)
		{
			shiftKeyDown = false;
		}
		if(keyCode == KeyEvent.KEYCODE_SEARCH)
		{
			ctrlKeyDown = false;
			return true;
		}
		return true;
	}

	private Handler handler = new Handler() {
		public void handleMessage(Message msg) {
			String s = TerminalReceive();
			if (s.length() != 0) {

				for (int i = 0; i < s.length(); i++) {
					char c = s.charAt(i);
					if (c < 32) {
						dbgTerminalTranscript.writeRaw('^');
						int a = c / 10;
						int b = c - a * 10;
						dbgTerminalTranscript.writeRaw((char) ('0' + a));
						dbgTerminalTranscript.writeRaw((char) ('0' + b));
					} else {
						dbgTerminalTranscript.writeRaw(c);
						dbgTerminalTranscript.invalidate();
					}
				}

				screen.write(s);
				if (screen.changeColumn1 <= screen.changeColumn2) {
					Rect cliprect = new Rect();
					cliprect.bottom = screen.computeCoordY(screen.changeRow2)
							+ screen.charHeight;
					cliprect.top = screen.computeCoordY(screen.changeRow1);
					cliprect.right = screen.computeCoordX(screen.changeColumn2)
							+ screen.charWidth;
					cliprect.left = screen.computeCoordX(screen.changeColumn1);
					screen.invalidate(cliprect);
				}
			}
		}
	};

	public void run() {
		while (true) {
			try {
				handler.sendEmptyMessage(0);
				Thread.sleep(10);
			} catch (InterruptedException e) {
				throw new RuntimeException(e.getMessage());
			}
		}
	}

	public void onDestroy() {
		TestShutdown();
	}

	public void doCommand(String command, String arg0, String arg1) {
		try {
			// android.os.Exec is not included in android.jar so we need to use
			// reflection.
			Class execClass = Class.forName("android.os.Exec");
			Method createSubprocess = execClass.getMethod("createSubprocess",
					String.class, String.class, String.class, int[].class);
			Method waitFor = execClass.getMethod("waitFor", int.class);

			// Executes the command.
			// NOTE: createSubprocess() is asynchronous.
			int[] pid = new int[1];
			FileDescriptor fd = (FileDescriptor) createSubprocess.invoke(null,
					command, arg0, arg1, pid);

			// Reads stdout.
			// NOTE: You can write to stdin of the command using new
			// FileOutputStream(fd).
			FileInputStream in = new FileInputStream(fd);
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(in));
			String output = "";
			try {
				String line;
				while ((line = reader.readLine()) != null) {
					output += line + "\n";
				}
			} catch (IOException e) {
				// It seems IOException is thrown when it reaches EOF.
			}

			// Waits for the command to finish.
			waitFor.invoke(null, pid[0]);

			// send output to the textbox
			// screen.write(output);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e.getMessage());
		} catch (SecurityException e) {
			throw new RuntimeException(e.getMessage());
		} catch (NoSuchMethodException e) {
			throw new RuntimeException(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw new RuntimeException(e.getMessage());
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e.getMessage());
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	public void copyAsset(String assetname) {
		String destname = "/data/data/com.nethackff/" + assetname;
		File newasset = new File(destname);
		try {
			newasset.createNewFile();
			BufferedOutputStream out = new BufferedOutputStream(
					new FileOutputStream(newasset));
			BufferedInputStream in = new BufferedInputStream(this.getAssets()
					.open(assetname));
			int b;
			while ((b = in.read()) != -1) {
				out.write(b);
			}
			out.flush();
			out.close();
			in.close();
		} catch (IOException ex) {
			screen.write("Failed to copy file '" + assetname + "'.\n");
		}
	}

	public void copyNetHackData() {
		AssetManager am = getResources().getAssets();
		String assets[] = null;
		try {
			assets = am.list("dat");

			for (int i = 0; i < assets.length; i++) {
				copyAsset("dat/" + assets[i]);
			}
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		int width = 80;
		int height = 22;

		screen = new TerminalView(this, width, height);

		dbgTerminalTranscript = new TerminalView(this, 80, 2);
		//dbgTerminalTranscript.colorForeground = TerminalView.kColRed;

		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);

		//layout.addView(dbgTerminalTranscript);
		layout.addView(screen);

		doCommand("/system/bin/mkdir", "/data/data/com.nethackff/dat", "");
		doCommand("/system/bin/mkdir", "/data/data/com.nethackff/dat/save", "");
		copyNetHackData();

		if (TestInit(width, height) == 0) {
			return;
		}

		setContentView(layout);

		Thread thread = new Thread(this);
		thread.start();
	}

	public native int TestInit(int numcols, int numrows);

	public native void TestShutdown();

	public native String TerminalReceive();

	public native void TerminalSend(String str);

	static {
		System.loadLibrary("nethack");
	}
}