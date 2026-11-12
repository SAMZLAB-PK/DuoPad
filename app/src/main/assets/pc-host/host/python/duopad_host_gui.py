"""DuoPad PC Host v5 — Windows desktop control centre.

A GUI around the same authenticated LAN/Tailscale protocol as unipoint_host.py.
The Host always needs an interactive logged-in Windows user for desktop capture.
It never silently installs a service, disables firewall, or exposes public ports.
"""
import json
import os
import queue
import secrets
import socket
import subprocess
import sys
import threading
import tkinter as tk
from pathlib import Path
from tkinter import messagebox
from PIL import Image, ImageTk
import qrcode

from unipoint_host import serve, build_pairing_uri, get_lan_ipv4s

BG = "#F5F6FA"
WHITE = "#FFFFFF"
INK = "#16202E"
SECONDARY = "#677284"
BLUE = "#285DDA"
LIGHT = "#EAF1FF"
GREEN = "#14825A"
GREY = "#DDE3EC"
FONT = "Segoe UI"
PORT = 27845


def resource(name):
    return Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent)) / name


def config_path():
    base = Path(os.environ.get("LOCALAPPDATA", str(Path.home()))) / "DuoPad" / "PC Host"
    base.mkdir(parents=True, exist_ok=True)
    return base / "config.json"


def load_pin():
    path = config_path()
    try:
        obj = json.loads(path.read_text(encoding="utf-8"))
        pin = str(obj.get("pin", ""))
        if 8 <= len(pin) <= 24 and pin.isdigit():
            return pin
    except (OSError, ValueError, TypeError):
        pass
    return reset_pin()


def reset_pin():
    pin = f"{secrets.randbelow(10**12):012d}"
    path = config_path()
    path.write_text(json.dumps({"pin": pin, "port": PORT}, indent=2), encoding="utf-8")
    return pin


def tailnet_ip():
    try:
        p = subprocess.run(["tailscale", "ip", "-4"], check=False,
            capture_output=True, text=True, timeout=3,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        return p.stdout.strip().splitlines()[0] if p.returncode == 0 and p.stdout.strip() else None
    except (FileNotFoundError, OSError, subprocess.TimeoutExpired):
        return None


class HostApp:
    def __init__(self, root):
        self.root = root
        self.root.title("DuoPad Pro — Windows PC Host")
        self.root.geometry("950x710")
        self.root.minsize(780, 630)
        self.root.configure(bg=BG)
        self.events = queue.Queue()
        self.stop_event = None
        self.thread = None
        self.online = False
        self.pin = load_pin()
        ips = get_lan_ipv4s()
        self.ip = ips[0] if ips else "127.0.0.1"
        self.remote_ip = tailnet_ip()
        self.build()
        self.root.after(120, self.poll_events)
        self.root.protocol("WM_DELETE_WINDOW", self.on_close)

    def label(self, parent, text, size=11, fg=INK, bold=False, bg=WHITE, **kw):
        return tk.Label(parent, text=text, fg=fg, bg=bg, font=(FONT, size, "bold" if bold else "normal"), **kw)

    def panel(self, parent):
        f = tk.Frame(parent, bg=WHITE, highlightbackground=GREY, highlightthickness=1)
        return f

    def button(self, parent, text, command, primary=False):
        return tk.Button(parent, text=text, command=command,
            font=(FONT, 10, "bold"), bg=BLUE if primary else WHITE,
            fg=WHITE if primary else INK, activebackground="#1642AB" if primary else LIGHT,
            activeforeground=WHITE if primary else INK, bd=0, relief="flat",
            padx=19, pady=11, cursor="hand2")

    def build(self):
        top = tk.Frame(self.root, bg=WHITE, height=108)
        top.pack(fill="x")
        top.pack_propagate(False)
        logo_file = resource("duopad_original_logo.png")
        try:
            picture = Image.open(logo_file).convert("RGBA").resize((72,72))
            self.logo = ImageTk.PhotoImage(picture)
            tk.Label(top, image=self.logo, bg=WHITE).pack(side="left", padx=(25,13), pady=16)
        except (OSError, ValueError):
            self.label(top, "D", 29, WHITE, True, INK, padx=18).pack(side="left", padx=22)
        title = tk.Frame(top, bg=WHITE)
        title.pack(side="left", pady=22)
        self.label(title, "DOUPAD  HOST", 21, INK, True).pack(anchor="w")
        self.label(title, "Secure PC control • Desktop • Files • Drives", 10, SECONDARY).pack(anchor="w")
        self.state_badge = self.label(top, "●  NOT RUNNING", 10, SECONDARY, True)
        self.state_badge.pack(side="right", padx=28)

        body = tk.Frame(self.root, bg=BG)
        body.pack(fill="both", expand=True, padx=23, pady=18)
        body.grid_columnconfigure(0, weight=3)
        body.grid_columnconfigure(1, weight=2)
        body.grid_rowconfigure(2, weight=1)

        left = self.panel(body)
        left.grid(row=0,column=0,sticky="nsew",padx=(0,12),pady=(0,14))
        content=tk.Frame(left,bg=WHITE)
        content.pack(fill="both",expand=True,padx=23,pady=19)
        self.label(content,"Your Windows PC",17,INK,True).pack(anchor="w")
        self.label(content,socket.gethostname(),11,SECONDARY).pack(anchor="w",pady=(0,15))

        self.label(content,"LOCAL CONNECTION",9,SECONDARY,True).pack(anchor="w")
        self.address=self.label(content,f"{self.ip}:{PORT}",18,INK,True)
        self.address.pack(anchor="w",pady=(2,13))
        self.label(content,"PAIRING PIN",9,SECONDARY,True).pack(anchor="w")
        self.pin_label=self.label(content,self.pin,21,INK,True)
        self.pin_label.pack(anchor="w",pady=(2,14))
        button_bar=tk.Frame(content,bg=WHITE)
        button_bar.pack(anchor="w",fill="x")
        self.start_button=self.button(button_bar,"Start Host",self.start,True)
        self.start_button.pack(side="left",padx=(0,8))
        self.stop_button=self.button(button_bar,"Stop",self.stop)
        self.stop_button.pack(side="left")
        self.stop_button.config(state="disabled")
        bottom=tk.Frame(content,bg=WHITE)
        bottom.pack(anchor="w",fill="x",pady=(14,0))
        self.button(bottom,"Copy Address",lambda:self.copy(f"{self.ip}:{PORT}")).pack(side="left")
        self.button(bottom,"Regenerate PIN",self.regenerate).pack(side="left",padx=(8,0))

        qr_panel=self.panel(body)
        qr_panel.grid(row=0,column=1,sticky="nsew",pady=(0,14))
        self.label(qr_panel,"Scan to connect",15,INK,True).pack(pady=(15,5))
        self.qr_label=tk.Label(qr_panel,bg=WHITE)
        self.qr_label.pack(pady=(1,4))
        self.label(qr_panel,"DuoPad Android  →  Scan QR",10,SECONDARY).pack(pady=(0,9))
        self.refresh_qr()

        remote=self.panel(body)
        remote.grid(row=1,column=0,columnspan=2,sticky="ew",pady=(0,14))
        info=tk.Frame(remote,bg=WHITE)
        info.pack(side="left",fill="x",expand=True,padx=22,pady=17)
        self.label(info,"REMOTE ACCESS  •  TAILSCALE",10,BLUE,True).pack(anchor="w")
        text=f"VPN address: {self.remote_ip}:{PORT}" if self.remote_ip else (
          "Install Tailscale on PC + phone, then use the PC's 100.x IP in DuoPad")
        self.label(info,text,10,SECONDARY).pack(anchor="w",pady=(3,0))
        self.button(remote,"Refresh VPN",self.refresh_remote).pack(side="right",padx=(0,20))

        logs=self.panel(body)
        logs.grid(row=2,column=0,columnspan=2,sticky="nsew")
        self.label(logs,"Connection activity",13,INK,True).pack(anchor="w",padx=20,pady=(12,7))
        self.logbox=tk.Text(logs,height=8,wrap="word",font=("Consolas",10),bg="#F8FAFC",
            fg=INK,relief="flat",bd=0,state="disabled",padx=13,pady=9)
        self.logbox.pack(fill="both",expand=True,padx=20,pady=(0,15))
        self.log("Host ready. Use Start Host, then connect your phone.")
        self.log("Keep this window open during LAN or VPN remote sessions.")

    def refresh_qr(self):
        uri=build_pairing_uri(self.ip,PORT,socket.gethostname(),self.pin)
        bitmap=qrcode.make(uri).convert("RGB").resize((176,176))
        self.qr_image=ImageTk.PhotoImage(bitmap)
        self.qr_label.config(image=self.qr_image)

    def refresh_remote(self):
        self.remote_ip=tailnet_ip()
        messagebox.showinfo("Tailscale", f"Remote PC IP: {self.remote_ip or 'Unavailable'}\n"
            "Both devices must be signed into the same Tailscale network.\n"
            "Do NOT forward TCP 27845 on your router.",parent=self.root)

    def copy(self, value):
        self.root.clipboard_clear()
        self.root.clipboard_append(value)
        self.log("Copied connection address.")

    def log(self,text):
        self.logbox.config(state="normal")
        self.logbox.insert("end",f"• {text}\n")
        self.logbox.see("end")
        self.logbox.config(state="disabled")

    def start(self):
        if self.thread and self.thread.is_alive():
            return
        self.stop_event=threading.Event()
        def run():
            try:
                serve(PORT,self.pin,stop_event=self.stop_event,
                    on_event=lambda event,payload:self.events.put((event,payload)))
            except Exception as error:
                self.events.put(("error",str(error)))
        self.thread=threading.Thread(target=run,daemon=True,name="DuoPadHostListener")
        self.thread.start()
        self.log("Starting TCP 27845 and UDP 27846…")
        self.start_button.config(state="disabled")
        self.stop_button.config(state="normal")

    def stop(self):
        if self.stop_event:self.stop_event.set()
        self.log("Stopping listener…")

    def regenerate(self):
        if self.thread and self.thread.is_alive():
            messagebox.showwarning("Stop Host first","Stop the Host before regenerating the PIN.",parent=self.root)
            return
        self.pin=reset_pin()
        self.pin_label.config(text=self.pin)
        self.refresh_qr()
        self.log("Pairing PIN regenerated. Saved phones need to pair again.")

    def poll_events(self):
        while True:
            try:event,payload=self.events.get_nowait()
            except queue.Empty:break
            if event=="ready":
                self.online=True
                self.state_badge.config(text="●  ONLINE",fg=GREEN)
                self.log("Host online — control and file access available.")
            elif event=="connected":
                self.log(f"Client connected: {payload[0]}")
            elif event=="disconnected":
                self.log(f"Client disconnected: {payload[0]}")
            elif event in ("stopped","error"):
                self.online=False
                self.state_badge.config(text="●  NOT RUNNING",fg=SECONDARY)
                self.start_button.config(state="normal")
                self.stop_button.config(state="disabled")
                self.log(f"Stopped. {payload if event=='error' else ''}".strip())
                if event=="error":messagebox.showerror("DuoPad Host",str(payload),parent=self.root)
        self.root.after(120,self.poll_events)

    def on_close(self):
        if self.stop_event:self.stop_event.set()
        self.root.destroy()


if __name__=="__main__":
    main=tk.Tk()
    HostApp(main)
    main.mainloop()
