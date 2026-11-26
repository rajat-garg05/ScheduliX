# ScheduliX — HOD Guide

This guide walks a Head of Department (HOD) through setting up a department, creating timetables, and managing daily substitutes in **ScheduliX**.

---

## 1. Register your department
1. Open the ScheduliX **Home** page and click **Register your department**.
2. Enter your **Department name**, **Official email**, and a **Password** (at least 10 characters).
3. Open the confirmation email sent to your address and click the verification link (works for 24 hours).
4. Sign in on **Log in** to open your **Dashboard**.

---

## 2. Set up department data (in order)

Under **Setup** in the left menu, add data in these four steps:

### A. Teachers (**Setup → Teachers**)
- Add teachers one by one with **Add teacher**, or click **Import file** to upload a CSV (`facultyCode,fullName,officialEmail,designation`).
- Every new teacher automatically gets an account using their official email and the default password `ScheduliX@123`. They must change it on their first login.
- If a teacher forgets their password, you can reset it back to `ScheduliX@123` from their row menu, or deactivate an account that is no longer teaching.

### B. Subjects (**Setup → Subjects**)
- Add theory subjects or labs with **Add subject**, or upload a CSV via **Import file** (`subjectCode,name,classification,periodsInARow,runsTogetherGroup,sharedPlace`).
- **Classification:** Choose `Theory` or `Lab`.
- **Lectures in a row:** Usually `1` for theory, or `2` (or more) for back-to-back lab blocks. Blocks never cross a break or lunch.
- **Runs at the same time as:** Give two labs the same group name (for example `LABGROUP-S3`) when the class splits in half at the same hour with two different teachers and different shared places.
- **Shared lab or place:** Optional name (for example `Computer Lab 1`). ScheduliX ensures no two classes use the same shared place at the same time across the department.

### C. Who can teach what (**Setup → Who can teach what**)
- Link each teacher to the subjects they are able to teach, either interactively (**By teacher** / **By subject**, then **Save changes**) or via CSV (**Import file**: `facultyCode,subjectCode`).

### D. Semesters, sections, and semester plans (**Setup → Semester plans**)
1. Under **1. Semesters**, click **Add** and create the semester (for example `3` / `Semester 3`).
2. Under **2. Sections**, click **Add** to add sections (for example `A`, `B`).
3. Under **3. Semester plan**, add each subject and its **Lectures / week** by hand and click **Save plan**, or click **Upload file** to import a CSV (`subjectCode,lecturesPerWeek`).
   - You can apply one CSV plan to multiple sections at once using **Use this file for other sections too**.
   - Sections with an already saved timetable are locked until that saved timetable is deleted.

---

## 3. Make a timetable (**Make Timetable**)

Click **Make Timetable → Start new timetable** (or resume a draft under **Unfinished timetables**):

1. **Step 1: Choose classes** — Pick one semester and tick the sections to schedule together, then click **Save and continue**.
2. **Step 2: Time settings** — Choose working days, start time, lecture length, lectures per day, daily limit per teacher, and breaks/lunch. Preview the day schedule on the right, then click **Save and continue**.
3. **Step 3: Check** — Click **Run check**. If ScheduliX finds missing teachers, mismatched lecture totals, or lab-room conflicts, follow the plain message and **Fix this** link. When everything passes, click **Continue to teachers**.
4. **Step 4: Teachers** — ScheduliX fills in suggested teachers automatically. Change any teacher from the dropdown if needed (warnings appear if a choice breaks limits or paired-lab rules), then click **Continue to fixed lectures**.
5. **Step 5: Fixed lectures** — Optionally pin specific lectures to exact days/slots (`+ Pin` or quick-add). Fixed lectures never move. Click **Make Timetable**.
6. **Step 6: Review and save** —
   - Browse tabs for each section and the **Teachers** tab.
   - Click **Keep this lecture** on any slots you like (or **Keep all**).
   - Click **Make again** to rearrange only the unpinned lectures.
   - Click **Save timetable** when satisfied. Saved timetables lock those sections, teachers, and shared places against future conflicts.

---

## 4. View or replace saved timetables (**Classes and Timetables**)
- Open **Classes and Timetables** to inspect any saved section timetable (`View` or switch between sections).
- Saved timetables cannot be edited in place; click **Delete** (with confirmation preview) to remove a saved timetable so you can rebuild it.

---

## 5. Leave, substitutes, and daily overview

### Leave (**Leave**)
- Click **Add leave** to record a whole-day or specific-lecture leave for a teacher; ScheduliX automatically prepares substitute proposals.
- When a teacher submits a request (`Waiting for HOD`), click **Accept** or **Reject** (with an optional note). Accepting prepares substitute proposals right away.

### Today (**Today**)
- Pick any date (defaults to today in your department timezone).
- Any lectures missing a teacher show **Needs review** with a suggested substitute (free at that exact clock time and eligible to teach the subject if available).
- Accept individuals or click **Accept all** to confirm substitutes and send notifications, or pick another free teacher manually from the dropdown and click **Check again** if plans change.

### Notifications & History
- Click the **bell icon** in the top bar for leave and timetable updates.
- Open **History** to audit a plain-sentence log of department actions.
