# OpenDiskDiver
Open toolbox for drive and file inspection and recovery
If you are able to mount your drive then theres still a chance to recover something.


# Status
This project is currently in an early and unfinished state!
Guessed progress: 20%

# Features
- interactive GUI (Console Application for Windows as well as a commandline-version for Linux & co. Operating Systems)
- guided disk copy function
- Partition inspector
- raw-sector inspector

- read MBR/GPT-Partition tables
- read NTFS partitions (including compressed files)
- read FAT partitions (FAT12, FAT16, FAT32 & ExFAT)
- write raw disk dumps (.img files)
- write ZIP-compressed disk dumps (drive data splited up into smaller blocks)

# TODO list
- Module System
- disk drive format parsers:
    - ext
- other parsers planned:
    - VDI
    - ZIP
    - ...

## Disclamer
Please use this tool only if you know what you are donig!
All damaged drives behave different and this program just tries to process all intact sectors!
I am not responsible for any damage caused by usage of this program! Use at your own risk.
