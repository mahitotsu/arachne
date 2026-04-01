package com.mahitotsu.arachne.strands.steering;

public sealed interface ToolSteeringAction permits Proceed, Guide, Interrupt {
}