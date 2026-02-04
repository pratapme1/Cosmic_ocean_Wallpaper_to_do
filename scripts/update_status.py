#!/usr/bin/env python3
import argparse
import datetime
from pathlib import Path

STATUS_PATH = Path(__file__).resolve().parents[1] / "docs" / "STATUS_TRACKING.md"


def update_active_queue(content: str, item: str, status: str, owner: str, notes: str | None):
    lines = content.splitlines()
    in_table = False
    updated = False
    for i, line in enumerate(lines):
        if line.strip() == "## Active Queue":
            in_table = True
            continue
        if in_table and line.startswith("## "):
            in_table = False
        if in_table and line.startswith("|") and "|" in line:
            cols = [c.strip() for c in line.strip("|").split("|")]
            if len(cols) >= 4 and cols[0] == item:
                cols[1] = owner or cols[1]
                cols[2] = status or cols[2]
                if notes is not None:
                    cols[3] = notes
                lines[i] = "| " + " | ".join(cols[:4]) + " |"
                updated = True
                break
    return "\n".join(lines), updated


def append_session_log(content: str, date: str, agent: str, task: str, result: str, evidence: str):
    lines = content.splitlines()
    out = []
    inserted = False
    for i, line in enumerate(lines):
        out.append(line)
        if line.strip() == "## Session Log":
            # Keep header lines, append after the table header if present
            continue
        if not inserted and line.startswith("| Date | Agent | Task | Result | Evidence |"):
            continue
        if not inserted and line.startswith("|---"):
            # Insert new row after separator
            out.append(f"| {date} | {agent} | {task} | {result} | {evidence} |")
            inserted = True
    if not inserted:
        out.append(f"| {date} | {agent} | {task} | {result} | {evidence} |")
    return "\n".join(out)


def main():
    parser = argparse.ArgumentParser(description="Update STATUS_TRACKING.md")
    parser.add_argument("--item", help="Active Queue item name")
    parser.add_argument("--status", help="Ready|In Progress|Blocked|Done")
    parser.add_argument("--owner", help="Owner name")
    parser.add_argument("--notes", help="Notes for active queue item")
    parser.add_argument("--log", action="store_true", help="Append to Session Log")
    parser.add_argument("--task", help="Task for session log")
    parser.add_argument("--result", help="Pass/Fail/Blocked")
    parser.add_argument("--evidence", default="", help="Evidence path")
    parser.add_argument("--agent", help="Agent name")
    parser.add_argument("--date", help="YYYY-MM-DD (defaults to today)")

    args = parser.parse_args()
    if not STATUS_PATH.exists():
        raise SystemExit(f"Missing {STATUS_PATH}")

    content = STATUS_PATH.read_text()

    if args.item and args.status:
        content, updated = update_active_queue(
            content, args.item, args.status, args.owner or "", args.notes
        )
        if not updated:
            raise SystemExit(f"Item not found in Active Queue: {args.item}")

    if args.log:
        date = args.date or datetime.date.today().isoformat()
        agent = args.agent or "Agent"
        task = args.task or ""
        result = args.result or ""
        evidence = args.evidence or ""
        content = append_session_log(content, date, agent, task, result, evidence)

    STATUS_PATH.write_text(content)


if __name__ == "__main__":
    main()
