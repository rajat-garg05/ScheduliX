import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, test } from '@playwright/test';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const sampleDir = path.resolve(__dirname, '../../backend/src/main/resources/sample');
const defaultLogPath = path.resolve(__dirname, '../../backend/dev-run.log');

function addDaysIso(isoDate, days) {
  const [y, m, d] = isoDate.split('-').map(Number);
  const dt = new Date(y, m - 1, d);
  dt.setDate(dt.getDate() + days);
  const yy = dt.getFullYear();
  const mm = String(dt.getMonth() + 1).padStart(2, '0');
  const dd = String(dt.getDate()).padStart(2, '0');
  return `${yy}-${mm}-${dd}`;
}

function nextMondayIso() {
  const d = new Date();
  const day = d.getDay(); // 0 = Sun, 1 = Mon, ...
  const daysUntilMonday = ((8 - day) % 7) || 7;
  d.setDate(d.getDate() + daysUntilMonday);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${dd}`;
}

function readLogSlice(logPath, previousByteOffset) {
  const buf = fs.readFileSync(logPath);
  const isUtf16Le = (buf.length >= 2 && buf[0] === 0xff && buf[1] === 0xfe) || buf.includes(0);
  let start = Math.min(previousByteOffset, buf.length);
  if (isUtf16Le && start % 2 !== 0) {
    start -= 1;
  }
  return buf.subarray(start).toString(isUtf16Le ? 'utf16le' : 'utf8').replace(/\uFEFF/g, '');
}

async function waitForVerificationUrl(logPath, previousLength) {
  const deadline = Date.now() + 30_000;
  const pattern = /https?:\/\/[^\s"'<>]+\/verify-email\?token=[^\s"'<>]+/g;

  while (Date.now() < deadline) {
    if (fs.existsSync(logPath)) {
      const fresh = readLogSlice(logPath, previousLength);
      const matches = fresh.match(pattern);
      if (matches && matches.length > 0) {
        return matches[matches.length - 1];
      }
    }
    await new Promise((r) => setTimeout(r, 300));
  }
  throw new Error(
    `Could not find a /verify-email link in ${logPath}. Start the backend with Tee-Object -FilePath dev-run.log.`
  );
}

test('full department journey from registration to teacher Today view', async ({ page }) => {
  test.setTimeout(300_000);
  const logPath = process.env.VERIFY_LINK_LOG || defaultLogPath;
  const initialLogSize = fs.existsSync(logPath) ? fs.statSync(logPath).size : 0;

  const stamp = Date.now();
  const deptName = `CSE Journey ${stamp}`;
  const hodEmail = `hod.e2e.${stamp}@college.edu`;
  const hodPassword = 'HodPass123!';
  const teacherEmail = 'arjun.mehta@cse-demo.edu';
  const teacherNewPassword = 'TeacherPass123!';

  // 1. Register a new department
  await page.goto('/register');
  await page.getByLabel(/department/i).fill(deptName);
  await page.getByLabel(/email/i).fill(hodEmail);
  await page.getByLabel(/^password/i).fill(hodPassword);
  await page.getByRole('button', { name: /register|create/i }).click();
  await expect(page).toHaveURL(/\/check-email/, { timeout: 30_000 });

  // 2. Confirm email using the link printed in the dev backend log
  const verifyUrl = await waitForVerificationUrl(logPath, initialLogSize);
  await page.goto(verifyUrl);
  await expect(page.getByText(/verified|confirmed/i)).toBeVisible({ timeout: 30_000 });

  // 3. Log in as HOD
  await page.goto('/login');
  await page.getByLabel(/email/i).fill(hodEmail);
  await page.getByLabel(/password/i).fill(hodPassword);
  await page.getByRole('button', { name: /log in|sign in/i }).click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 30_000 });

  // 4. Import sample CSVs: Teachers, Subjects, Who can teach what
  await page.goto('/setup/teachers');
  await page.getByRole('button', { name: /import file/i }).click();
  await page.locator('input[type="file"]').setInputFiles(path.join(sampleDir, 'teachers.csv'));
  await expect(page.getByRole('button', { name: /confirm import/i })).toBeEnabled({ timeout: 30_000 });
  await page.getByRole('button', { name: /confirm import/i }).click();
  await expect(page.getByRole('heading', { name: /import teachers from csv/i })).toBeHidden({
    timeout: 60_000,
  });
  await expect(page.getByText(/import complete:/i)).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText('Prof. Arjun Mehta')).toBeVisible({ timeout: 30_000 });

  await page.goto('/setup/subjects');
  await page.getByRole('button', { name: /import file/i }).click();
  await page.locator('input[type="file"]').setInputFiles(path.join(sampleDir, 'subjects.csv'));
  await expect(page.getByRole('button', { name: /confirm import/i })).toBeEnabled({ timeout: 30_000 });
  await page.getByRole('button', { name: /confirm import/i }).click();
  await expect(page.getByRole('heading', { name: /import subjects from csv/i })).toBeHidden({
    timeout: 60_000,
  });
  await expect(page.getByText(/import complete:/i)).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText('Data Structures', { exact: true })).toBeVisible({ timeout: 30_000 });

  await page.goto('/setup/teacher-subjects');
  await page.getByRole('button', { name: /import file/i }).click();
  await page
    .locator('input[type="file"]')
    .setInputFiles(path.join(sampleDir, 'who_can_teach_what.csv'));
  await expect(page.getByRole('button', { name: /confirm import/i })).toBeEnabled({ timeout: 30_000 });
  await page.getByRole('button', { name: /confirm import/i }).click();
  await expect(page.getByRole('heading', { name: /import who can teach what from csv/i })).toBeHidden({
    timeout: 60_000,
  });
  await expect(page.getByText(/import complete:/i)).toBeVisible({ timeout: 30_000 });

  // 5. Set up Semester 3 Sections A and B with semester3_plan.csv
  await page.goto('/setup/semester-plans');
  await page.getByRole('button', { name: /^add$/i }).nth(0).click();
  await page.getByLabel(/semester number/i).fill('3');
  await page.getByLabel(/semester name/i).fill('Semester 3');
  await page.getByRole('button', { name: /^save$/i }).click();
  await expect(page.getByText('Semester 3', { exact: true })).toBeVisible();

  for (const sec of ['A', 'B']) {
    await page.getByRole('button', { name: /^add$/i }).nth(1).click();
    await page.getByLabel(/section name/i).fill(sec);
    await page.getByRole('button', { name: /^save$/i }).click();
    await expect(page.getByText(`Section ${sec}`, { exact: true })).toBeVisible();
  }

  await page.getByRole('button', { name: /upload file/i }).click();
  await page
    .locator('input[type="file"]')
    .setInputFiles(path.join(sampleDir, 'semester3_plan.csv'));
  await page.getByRole('checkbox', { name: /section [ab]/i }).check();
  await expect(page.getByText(/replace the existing semester plan/i)).toBeVisible({ timeout: 20_000 });
  await page.getByLabel(/replace the existing semester plan/i).check();
  await expect(page.getByRole('button', { name: /confirm upload/i })).toBeEnabled({ timeout: 30_000 });
  await page.getByRole('button', { name: /confirm upload/i }).click();
  await expect(page.getByRole('heading', { name: /upload semester plan/i })).toBeHidden({ timeout: 60_000 });
  await expect(page.getByText(/saved plan for/i)).toBeVisible({ timeout: 30_000 });

  // 6. Make Timetable for Semester 3 Sections A and B
  await page.goto('/make-timetable');
  await page.getByRole('button', { name: /start new timetable/i }).click();

  // Step 1: choose Semester 3 + Sections A and B
  await page.getByLabel(/^semester$/i).selectOption({ label: 'Semester 3' });
  await expect(page.getByLabel(/section a/i)).toBeEnabled({ timeout: 20_000 });
  await page.getByLabel(/section a/i).check();
  await page.getByLabel(/section b/i).check();
  await page.getByRole('button', { name: /save and continue/i }).click();

  // Step 2: Time settings (keep defaults)
  await expect(page.getByRole('heading', { name: /step 2/i })).toBeVisible();
  await page.getByRole('button', { name: /save and continue/i }).click();

  // Step 3: Check
  await page.getByRole('button', { name: /run check/i }).click();
  await expect(page.getByRole('button', { name: /continue to teachers/i })).toBeEnabled({ timeout: 20_000 });
  await page.getByRole('button', { name: /continue to teachers/i }).click();

  // Step 4: Teachers -> wait for auto-suggest / rows ready, then Continue
  const continueToFixedBtn = page.getByRole('button', { name: /continue to fixed lectures/i });
  await expect(continueToFixedBtn).toBeEnabled({ timeout: 30_000 });
  await continueToFixedBtn.click();

  // Step 5: Fixed lectures -> Make Timetable
  await page.getByRole('button', { name: /make timetable/i }).click();

  // Step 6: Result -> Keep two lectures, Make again, then Save
  await expect(page.getByRole('heading', { name: /review and save/i })).toBeVisible({
    timeout: 60_000,
  });

  const keepButtons = page.getByRole('button', { name: /keep this lecture/i });
  await keepButtons.nth(0).click();
  await expect(page.getByText(/1 of \d+ pinned/i)).toBeVisible();
  await keepButtons.nth(0).click();
  await expect(page.getByText(/2 of \d+ pinned/i)).toBeVisible();

  await page.getByRole('button', { name: /make again/i }).click();
  await expect(page.getByText(/updated all unpinned lectures/i)).toBeVisible({ timeout: 60_000 });

  await page.getByRole('button', { name: /save timetable/i }).click();
  await page.getByRole('button', { name: /^save timetable$/i }).last().click();
  await expect(page).toHaveURL(/\/timetables/, { timeout: 30_000 });
  await expect(page.getByText('Section A', { exact: true })).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText('Section B', { exact: true })).toBeVisible({ timeout: 30_000 });

  // 7. Add leave for Prof. Arjun Mehta across next Mon-Fri so whichever weekday he teaches is covered
  const weekMonday = nextMondayIso();
  const weekFriday = addDaysIso(weekMonday, 4);
  let leaveDate = weekMonday;
  await page.goto('/leave');
  await page.getByRole('button', { name: /add leave/i }).click();
  await page.locator('#leaveTeacher').selectOption({ label: 'Prof. Arjun Mehta (T003)' });
  await page.locator('#leaveFrom').fill(weekMonday);
  await page.locator('#leaveTo').fill(weekFriday);
  await page.locator('#leaveReason').fill('Department conference');
  await page.getByRole('button', { name: /save leave/i }).click();
  await expect(page.getByText(/leave recorded/i)).toBeVisible({ timeout: 30_000 });

  // 8. Open Today, find the weekday with Prof. Arjun Mehta's proposed substitutes, and accept all
  await page.goto('/today');
  const acceptAllBtn = page.getByRole('button', { name: /accept all/i });
  for (let offset = 0; offset < 5; offset++) {
    const candidate = addDaysIso(weekMonday, offset);
    const todayLoaded = page
      .waitForResponse(
        (res) =>
          res.url().includes('/api/today') &&
          res.url().includes(candidate) &&
          res.status() === 200,
        { timeout: 30_000 }
      )
      .catch(() => null);
    await page.locator('input[type="date"]').fill(candidate);
    await todayLoaded;
    await expect(page.getByRole('button', { name: /check again/i })).toBeEnabled({ timeout: 30_000 });
    const hasAcceptAll = await acceptAllBtn
      .waitFor({ state: 'visible', timeout: 1500 })
      .then(() => true)
      .catch(() => false);
    if (hasAcceptAll) {
      leaveDate = candidate;
      break;
    }
  }
  await expect(acceptAllBtn).toBeVisible({ timeout: 30_000 });
  await acceptAllBtn.click();
  await expect(page.getByText(/accepted \d+ substitute/i)).toBeVisible({ timeout: 30_000 });

  // 9. Log out as HOD, log in as Prof. Arjun Mehta, change default password, and view Today
  await page.locator('header button').last().click();
  await page.getByRole('button', { name: /log out/i }).click();
  await expect(page).toHaveURL(/\/login|\/$/, { timeout: 30_000 });

  await page.goto('/login');
  await page.getByLabel(/email/i).fill(teacherEmail);
  await page.getByLabel(/password/i).fill('ScheduliX@123');
  await page.getByRole('button', { name: /log in|sign in/i }).click();

  // Forced default-password change
  await expect(page).toHaveURL(/\/change-password/, { timeout: 30_000 });
  await page.getByLabel(/current password/i).fill('ScheduliX@123');
  await page.getByLabel(/new password/i).first().fill(teacherNewPassword);
  const confirmField = page.getByLabel(/confirm/i);
  if (await confirmField.count()) {
    await confirmField.fill(teacherNewPassword);
  }
  await page.getByRole('button', { name: /update password|change password|save/i }).click();
  await expect(page).toHaveURL(/\/dashboard/, { timeout: 30_000 });

  // Teacher views Today for the leave date and sees accepted substitutes read-only
  await page.goto('/today');
  const teacherTodayLoaded = page
    .waitForResponse(
      (res) =>
        res.url().includes('/api/today') &&
        res.url().includes(leaveDate) &&
        res.status() === 200,
      { timeout: 30_000 }
    )
    .catch(() => null);
  await page.locator('input[type="date"]').fill(leaveDate);
  await teacherTodayLoaded;
  await expect(page.getByText('Substitute').first()).toBeVisible({
    timeout: 30_000,
  });
  await expect(page.getByRole('button', { name: /accept all/i })).toHaveCount(0);
});