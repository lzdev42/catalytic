import os
import pty
import serial
import time
import select

# Config
SERIAL_PORT = "/tmp/ttyV1"

def main():
    print(f"Starting Simulator on {SERIAL_PORT}...")
    
    # Wait for port to be created by socat
    while not os.path.exists(SERIAL_PORT):
        time.sleep(1)
        print(f"Waiting for {SERIAL_PORT}...")

    try:
        ser = serial.Serial(SERIAL_PORT, 9600, timeout=1)
        print(f"Connected to {SERIAL_PORT}")
        
        while True:
            # Non-blocking read
            if ser.in_waiting > 0:
                line = ser.readline().decode('utf-8').strip()
                if not line:
                    continue
                    
                print(f"Received: {line}")
                
                response = ""
                if line == "MEAS:VOLT?":
                    # Case 1: Scientific Notation
                    response = "3.305E+00\n"
                    
                elif line == "READ:VOLT":
                    # Case 2: Key-Value
                    response = "VOLTAGE:3.305V\n"
                    
                elif line == "MEAS:ALL?":
                    # Case 3: CSV
                    response = "12.5,0.15,PASS\n"
                    
                elif line == "MEAS:NOISY?":
                    # Case 4: Multi-line noise
                    # Note: Our simple SerialPlugin reads until newline. 
                    # If we send multiple lines, it might only read the first one unless 
                    # the plugin logic handles it. 
                    # For this MVP, let's test if regex works on a single complex line, 
                    # or acknowledge the limitation of readline-based plugin.
                    # Let's send it as one burst.
                    response = "DEBUG:Start\nWARN:LowBat\nVOLTAGE:3.305V\nDEBUG:End\n"
                
                if response:
                    print(f"Sending: {response.strip()}")
                    ser.write(response.encode('utf-8'))
                    
            time.sleep(0.01)
            
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    main()
