#!/usr/bin/env python3
"""
KISS Profile 모드 성능 로그 분석기

안드로이드 Profile 빌드에서 생성된 성능 로그를 분석하여
메모리 사용량, CPU 사용률, 검색 성능 등을 시각화합니다.

사용법:
    python3 analyze_profile_logs.py [로그_디렉토리_경로]
    
요구사항:
    pip install pandas matplotlib seaborn
"""

import os
import sys
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
from datetime import datetime, timedelta
import argparse
import glob

def setup_plot_style():
    """플롯 스타일 설정"""
    plt.style.use('seaborn-v0_8')
    sns.set_palette("husl")
    plt.rcParams['figure.figsize'] = (12, 8)
    plt.rcParams['font.size'] = 10

def load_performance_logs(log_dir):
    """성능 로그 파일들을 로드하여 DataFrame으로 반환"""
    log_files = glob.glob(os.path.join(log_dir, "performance_*.csv"))
    
    if not log_files:
        print(f"❌ {log_dir}에서 성능 로그 파일을 찾을 수 없습니다.")
        print("📱 안드로이드 기기에서 다음 경로를 확인하세요:")
        print("   /storage/emulated/0/Android/data/fr.neamar.kiss.lum7671/files/kiss_profile_logs/")
        return None
    
    print(f"📊 발견된 로그 파일: {len(log_files)}개")
    
    all_data = []
    for log_file in sorted(log_files):
        try:
            df = pd.read_csv(log_file)
            df['log_file'] = os.path.basename(log_file)
            all_data.append(df)
            print(f"✅ 로드됨: {log_file} ({len(df)} 레코드)")
        except Exception as e:
            print(f"❌ 로드 실패: {log_file} - {e}")
    
    if not all_data:
        return None
    
    combined_df = pd.concat(all_data, ignore_index=True)
    
    # 타임스탬프를 datetime으로 변환
    combined_df['datetime'] = pd.to_datetime(combined_df['timestamp'], unit='ms')
    
    # 커스텀 이벤트와 일반 데이터 분리
    performance_data = combined_df[combined_df['uptime_ms'] != 'CUSTOM_EVENT'].copy()
    custom_events = combined_df[combined_df['uptime_ms'] == 'CUSTOM_EVENT'].copy()
    
    # 숫자 컬럼 변환
    numeric_columns = ['uptime_ms', 'heap_used_mb', 'heap_max_mb', 'native_heap_mb', 
                      'cpu_usage_percent', 'gc_count', 'thread_count', 'memory_class_mb',
                      'large_memory_class_mb', 'available_memory_mb', 'total_memory_mb']
    
    for col in numeric_columns:
        if col in performance_data.columns:
            performance_data[col] = pd.to_numeric(performance_data[col], errors='coerce')
    
    return performance_data, custom_events

def analyze_memory_usage(df):
    """메모리 사용량 분석"""
    print("\n📊 메모리 사용량 분석")
    print("=" * 50)
    
    # 기본 통계
    heap_stats = df['heap_used_mb'].describe()
    print(f"🔹 힙 메모리 사용량 (MB):")
    print(f"   평균: {heap_stats['mean']:.2f}")
    print(f"   최대: {heap_stats['max']:.2f}")
    print(f"   최소: {heap_stats['min']:.2f}")
    print(f"   표준편차: {heap_stats['std']:.2f}")
    
    native_stats = df['native_heap_mb'].describe()
    print(f"🔹 네이티브 메모리 사용량 (MB):")
    print(f"   평균: {native_stats['mean']:.2f}")
    print(f"   최대: {native_stats['max']:.2f}")
    
    # 메모리 추세 분석
    df_sorted = df.sort_values('datetime')
    memory_trend = df_sorted['heap_used_mb'].rolling(window=10).mean()
    
    if memory_trend.iloc[-1] > memory_trend.iloc[0] * 1.2:
        print("⚠️  메모리 사용량이 지속적으로 증가하는 추세입니다 (메모리 누수 가능성)")
    elif memory_trend.std() > memory_trend.mean() * 0.3:
        print("📈 메모리 사용량이 불안정합니다")
    else:
        print("✅ 메모리 사용량이 안정적입니다")

def analyze_cpu_performance(df):
    """CPU 성능 분석"""
    print("\n⚡ CPU 성능 분석")
    print("=" * 50)
    
    cpu_stats = df['cpu_usage_percent'].describe()
    print(f"🔹 CPU 사용률 (%):")
    print(f"   평균: {cpu_stats['mean']:.2f}")
    print(f"   최대: {cpu_stats['max']:.2f}")
    print(f"   90%ile: {df['cpu_usage_percent'].quantile(0.9):.2f}")
    
    # 고 CPU 사용 구간 분석
    high_cpu_threshold = 70
    high_cpu_count = len(df[df['cpu_usage_percent'] > high_cpu_threshold])
    if high_cpu_count > 0:
        percentage = (high_cpu_count / len(df)) * 100
        print(f"⚠️  고 CPU 사용률 ({high_cpu_threshold}% 이상): {high_cpu_count}회 ({percentage:.1f}%)")
    else:
        print("✅ CPU 사용률이 안정적입니다")

def analyze_app_lifecycle(custom_events):
    """앱 생명주기 분석"""
    if custom_events.empty:
        return
    
    print("\n🔄 앱 생명주기 분석")
    print("=" * 50)
    
    # 생명주기 이벤트 카운트
    lifecycle_events = custom_events[custom_events['heap_used_mb'] == 'ACTIVITY_LIFECYCLE']
    if not lifecycle_events.empty:
        event_counts = lifecycle_events['native_heap_mb'].value_counts()
        print("🔹 액티비티 생명주기 이벤트:")
        for event, count in event_counts.items():
            print(f"   {event}: {count}회")

def analyze_search_performance(custom_events):
    """검색 성능 분석"""
    if custom_events.empty:
        return
    
    print("\n🔍 검색 성능 분석")
    print("=" * 50)
    
    search_events = custom_events[custom_events['heap_used_mb'] == 'SEARCH_PERFORMANCE']
    if not search_events.empty:
        # 검색 시간 추출 (간단한 파싱)
        search_times = []
        for detail in search_events['native_heap_mb']:
            try:
                if 'duration:' in detail:
                    duration_str = detail.split('duration:')[1].split('ms')[0]
                    search_times.append(int(duration_str))
            except:
                continue
        
        if search_times:
            avg_search_time = sum(search_times) / len(search_times)
            max_search_time = max(search_times)
            print(f"🔹 검색 성능:")
            print(f"   총 검색 횟수: {len(search_times)}")
            print(f"   평균 검색 시간: {avg_search_time:.2f}ms")
            print(f"   최대 검색 시간: {max_search_time}ms")
            
            if avg_search_time > 100:
                print("⚠️  검색 성능이 느립니다 (100ms 이상)")
            else:
                print("✅ 검색 성능이 양호합니다")

def create_visualizations(df, custom_events, output_dir):
    """성능 데이터 시각화"""
    print(f"\n📈 시각화 생성 중... (저장 위치: {output_dir})")
    
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)
    
    # 1. 메모리 사용량 추이
    plt.figure(figsize=(15, 10))
    
    plt.subplot(2, 2, 1)
    df_sorted = df.sort_values('datetime')
    plt.plot(df_sorted['datetime'], df_sorted['heap_used_mb'], label='Heap Memory', alpha=0.7)
    plt.plot(df_sorted['datetime'], df_sorted['native_heap_mb'], label='Native Memory', alpha=0.7)
    plt.title('메모리 사용량 추이')
    plt.xlabel('시간')
    plt.ylabel('메모리 (MB)')
    plt.legend()
    plt.xticks(rotation=45)
    
    # 2. CPU 사용률 분포
    plt.subplot(2, 2, 2)
    plt.hist(df['cpu_usage_percent'], bins=30, alpha=0.7, color='orange')
    plt.title('CPU 사용률 분포')
    plt.xlabel('CPU 사용률 (%)')
    plt.ylabel('빈도')
    
    # 3. 스레드 수 변화
    plt.subplot(2, 2, 3)
    plt.plot(df_sorted['datetime'], df_sorted['thread_count'], color='green', alpha=0.7)
    plt.title('스레드 수 변화')
    plt.xlabel('시간')
    plt.ylabel('스레드 수')
    plt.xticks(rotation=45)
    
    # 4. 메모리 vs CPU 상관관계
    plt.subplot(2, 2, 4)
    plt.scatter(df['heap_used_mb'], df['cpu_usage_percent'], alpha=0.5)
    plt.title('메모리 사용량 vs CPU 사용률')
    plt.xlabel('힙 메모리 (MB)')
    plt.ylabel('CPU 사용률 (%)')
    
    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, 'performance_overview.png'), dpi=300, bbox_inches='tight')
    print("✅ performance_overview.png 생성됨")
    
    # 메모리 상세 분석
    plt.figure(figsize=(12, 8))
    plt.plot(df_sorted['datetime'], df_sorted['heap_used_mb'], label='사용 중 힙', linewidth=2)
    plt.plot(df_sorted['datetime'], df_sorted['heap_max_mb'], label='최대 힙', linestyle='--', alpha=0.7)
    plt.plot(df_sorted['datetime'], df_sorted['available_memory_mb'], label='사용 가능 시스템 메모리', alpha=0.7)
    plt.title('상세 메모리 분석')
    plt.xlabel('시간')
    plt.ylabel('메모리 (MB)')
    plt.legend()
    plt.xticks(rotation=45)
    plt.grid(True, alpha=0.3)
    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, 'memory_analysis.png'), dpi=300, bbox_inches='tight')
    print("✅ memory_analysis.png 생성됨")

def generate_report(df, custom_events, output_dir):
    """HTML 보고서 생성"""
    report_path = os.path.join(output_dir, 'profile_report.html')
    
    html_content = f"""
    <!DOCTYPE html>
    <html>
    <head>
        <title>KISS Profile 성능 분석 보고서</title>
        <meta charset="utf-8">
        <style>
            body {{ font-family: Arial, sans-serif; margin: 40px; }}
            .header {{ background: #f0f0f0; padding: 20px; border-radius: 5px; }}
            .section {{ margin: 20px 0; }}
            .metric {{ background: #f9f9f9; padding: 10px; margin: 5px 0; border-left: 4px solid #007acc; }}
            .warning {{ border-left-color: #ff6b35; }}
            .success {{ border-left-color: #28a745; }}
            img {{ max-width: 100%; margin: 10px 0; }}
        </style>
    </head>
    <body>
        <div class="header">
            <h1>📱 KISS Profile 성능 분석 보고서</h1>
            <p>생성 시간: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}</p>
            <p>분석 기간: {df['datetime'].min()} ~ {df['datetime'].max()}</p>
            <p>총 데이터 포인트: {len(df)}개</p>
        </div>
        
        <div class="section">
            <h2>📊 성능 지표 요약</h2>
            <div class="metric">
                <strong>평균 힙 메모리 사용량:</strong> {df['heap_used_mb'].mean():.2f} MB
            </div>
            <div class="metric">
                <strong>최대 힙 메모리 사용량:</strong> {df['heap_used_mb'].max():.2f} MB
            </div>
            <div class="metric">
                <strong>평균 CPU 사용률:</strong> {df['cpu_usage_percent'].mean():.2f}%
            </div>
            <div class="metric">
                <strong>평균 스레드 수:</strong> {df['thread_count'].mean():.1f}개
            </div>
        </div>
        
        <div class="section">
            <h2>📈 성능 차트</h2>
            <img src="performance_overview.png" alt="성능 개요">
            <img src="memory_analysis.png" alt="메모리 분석">
        </div>
        
        <div class="section">
            <h2>💡 개선 권장사항</h2>
    """
    
    # 개선 권장사항 추가
    if df['heap_used_mb'].max() > 100:
        html_content += '<div class="metric warning">⚠️ 힙 메모리 사용량이 높습니다. 메모리 최적화를 고려하세요.</div>'
    
    if df['cpu_usage_percent'].mean() > 50:
        html_content += '<div class="metric warning">⚠️ 평균 CPU 사용률이 높습니다. 알고리즘 최적화를 고려하세요.</div>'
    
    if df['thread_count'].max() > 20:
        html_content += '<div class="metric warning">⚠️ 스레드 수가 많습니다. 스레드 풀 관리를 검토하세요.</div>'
    
    html_content += """
            <div class="metric success">✅ 자세한 분석은 터미널 출력을 참조하세요.</div>
        </div>
    </body>
    </html>
    """
    
    with open(report_path, 'w', encoding='utf-8') as f:
        f.write(html_content)
    
    print(f"✅ HTML 보고서 생성됨: {report_path}")

def analyze_user_actions(custom_events):
    """사용자 액션별 성능 분석"""
    if custom_events.empty:
        return
    
    print("\n🎯 사용자 액션별 성능 분석")
    print("=" * 50)
    
    # 액션별 이벤트 분류
    action_events = {}
    performance_snapshots = {}
    
    for idx, event in custom_events.iterrows():
        event_type = event['heap_used_mb']
        details = event['native_heap_mb']
        
        if event_type == 'PERFORMANCE_SNAPSHOT':
            # 성능 스냅샷 파싱
            try:
                snapshot_data = {}
                for item in details.split(','):
                    if ':' in item:
                        key, value = item.split(':', 1)
                        snapshot_data[key] = value
                
                context = snapshot_data.get('context', 'UNKNOWN')
                if context not in performance_snapshots:
                    performance_snapshots[context] = []
                performance_snapshots[context].append({
                    'timestamp': event['timestamp'],
                    'memory_mb': float(snapshot_data.get('memory_mb', 0)),
                    'memory_delta': float(snapshot_data.get('memory_delta', 0)),
                    'threads': int(snapshot_data.get('threads', 0)),
                    'action': snapshot_data.get('action', 'UNKNOWN')
                })
            except:
                continue
        
        elif event_type in ['SCROLL_ACTION', 'SEARCH_DETAILED', 'UI_INTERACTION', 'STARTUP_PHASE']:
            if event_type not in action_events:
                action_events[event_type] = []
            action_events[event_type].append({
                'timestamp': event['timestamp'],
                'details': details
            })
    
    # 스크롤 성능 분석
    if 'SCROLL_ACTION' in action_events:
        print("📜 스크롤 성능 분석:")
        scroll_events = action_events['SCROLL_ACTION']
        scroll_directions = {}
        for event in scroll_events:
            try:
                detail_dict = dict(item.split(':') for item in event['details'].split(',') if ':' in item)
                direction = detail_dict.get('direction', 'UNKNOWN')
                velocity = float(detail_dict.get('velocity', 0))
                if direction not in scroll_directions:
                    scroll_directions[direction] = []
                scroll_directions[direction].append(velocity)
            except:
                continue
        
        for direction, velocities in scroll_directions.items():
            avg_velocity = sum(velocities) / len(velocities)
            print(f"   {direction}: {len(velocities)}회, 평균 속도: {avg_velocity:.2f}")
    
    # 검색 성능 상세 분석
    if 'SEARCH_DETAILED' in action_events:
        print("🔍 검색 성능 상세 분석:")
        search_events = action_events['SEARCH_DETAILED']
        search_phases = {}
        for event in search_events:
            try:
                detail_dict = dict(item.split(':') for item in event['details'].split(',') if ':' in item)
                phase = detail_dict.get('phase', 'UNKNOWN')
                if phase not in search_phases:
                    search_phases[phase] = 0
                search_phases[phase] += 1
            except:
                continue
        
        for phase, count in search_phases.items():
            print(f"   {phase}: {count}회")
    
    # UI 상호작용 분석
    if 'UI_INTERACTION' in action_events:
        print("🖱️ UI 상호작용 성능:")
        ui_events = action_events['UI_INTERACTION']
        interaction_times = {}
        for event in ui_events:
            try:
                detail_dict = dict(item.split(':') for item in event['details'].split(',') if ':' in item)
                interaction_type = detail_dict.get('type', 'UNKNOWN')
                response_time = int(detail_dict.get('response_time', '0').replace('ms', ''))
                if interaction_type not in interaction_times:
                    interaction_times[interaction_type] = []
                interaction_times[interaction_type].append(response_time)
            except:
                continue
        
        for interaction, times in interaction_times.items():
            avg_time = sum(times) / len(times)
            max_time = max(times)
            print(f"   {interaction}: 평균 {avg_time:.2f}ms, 최대 {max_time}ms ({len(times)}회)")
    
    # 성능 스냅샷 분석
    if performance_snapshots:
        print("📸 액션별 성능 스냅샷:")
        for context, snapshots in performance_snapshots.items():
            if len(snapshots) > 1:
                memory_changes = [s['memory_delta'] for s in snapshots]
                avg_memory_change = sum(memory_changes) / len(memory_changes)
                max_memory_change = max(memory_changes)
                
                if abs(avg_memory_change) > 1.0 or abs(max_memory_change) > 5.0:  # 1MB 이상 변화
                    print(f"   {context}: 평균 메모리 변화 {avg_memory_change:.2f}MB, 최대 {max_memory_change:.2f}MB")
                    if max_memory_change > 10.0:
                        print(f"      ⚠️ 큰 메모리 증가 감지! ({max_memory_change:.2f}MB)")

def analyze_performance_patterns(df, custom_events):
    """성능 패턴 및 이상 징후 분석"""
    print("\n🔍 성능 패턴 분석")
    print("=" * 50)
    
    # 시간대별 성능 분석
    df['hour'] = pd.to_datetime(df['timestamp'], unit='ms').dt.hour
    hourly_cpu = df.groupby('hour')['cpu_usage_percent'].mean()
    hourly_memory = df.groupby('hour')['heap_used_mb'].mean()
    
    print("⏰ 시간대별 평균 성능:")
    for hour in sorted(hourly_cpu.index):
        print(f"   {hour:02d}시: CPU {hourly_cpu[hour]:.1f}%, 메모리 {hourly_memory[hour]:.1f}MB")
    
    # 성능 급변 구간 탐지
    df_sorted = df.sort_values('timestamp')
    memory_diff = df_sorted['heap_used_mb'].diff()
    cpu_diff = df_sorted['cpu_usage_percent'].diff()
    
    memory_spikes = df_sorted[abs(memory_diff) > 10]  # 10MB 이상 급변
    cpu_spikes = df_sorted[abs(cpu_diff) > 30]        # 30% 이상 급변
    
    if not memory_spikes.empty:
        print(f"📈 메모리 급변 구간: {len(memory_spikes)}회")
        for idx, spike in memory_spikes.head(5).iterrows():
            spike_time = pd.to_datetime(spike['timestamp'], unit='ms')
            print(f"   {spike_time}: {memory_diff.loc[idx]:+.1f}MB 변화")
    
    if not cpu_spikes.empty:
        print(f"⚡ CPU 급변 구간: {len(cpu_spikes)}회")
        for idx, spike in cpu_spikes.head(5).iterrows():
            spike_time = pd.to_datetime(spike['timestamp'], unit='ms')
            print(f"   {spike_time}: {cpu_diff.loc[idx]:+.1f}% 변화")

def create_action_performance_visualizations(df, custom_events, output_dir):
    """사용자 액션별 성능 시각화"""
    print(f"\n📊 액션별 성능 시각화 생성 중...")
    
    # 액션 타임라인 차트
    plt.figure(figsize=(15, 12))
    
    # 1. 메모리 사용량과 액션 이벤트 타임라인
    plt.subplot(3, 1, 1)
    df_sorted = df.sort_values('datetime')
    plt.plot(df_sorted['datetime'], df_sorted['heap_used_mb'], label='힙 메모리', alpha=0.7, color='blue')
    
    # 커스텀 이벤트를 타임라인에 표시
    if not custom_events.empty:
        search_events = custom_events[custom_events['heap_used_mb'] == 'SEARCH_DETAILED']
        scroll_events = custom_events[custom_events['heap_used_mb'] == 'SCROLL_ACTION']
        
        if not search_events.empty:
            search_times = pd.to_datetime(search_events['timestamp'], unit='ms')
            plt.scatter(search_times, [50] * len(search_times), 
                       color='red', label='검색 이벤트', s=30, alpha=0.7)
        
        if not scroll_events.empty:
            scroll_times = pd.to_datetime(scroll_events['timestamp'], unit='ms')
            plt.scatter(scroll_times, [40] * len(scroll_times), 
                       color='green', label='스크롤 이벤트', s=20, alpha=0.7)
    
    plt.title('메모리 사용량과 사용자 액션 타임라인')
    plt.ylabel('메모리 (MB)')
    plt.legend()
    plt.grid(True, alpha=0.3)
    
    # 2. CPU 사용률과 액션 이벤트
    plt.subplot(3, 1, 2)
    plt.plot(df_sorted['datetime'], df_sorted['cpu_usage_percent'], label='CPU 사용률', alpha=0.7, color='orange')
    
    if not custom_events.empty:
        search_events = custom_events[custom_events['heap_used_mb'] == 'SEARCH_DETAILED']
        if not search_events.empty:
            search_times = pd.to_datetime(search_events['timestamp'], unit='ms')
            plt.scatter(search_times, [70] * len(search_times), 
                       color='red', label='검색 이벤트', s=30, alpha=0.7)
    
    plt.title('CPU 사용률과 사용자 액션')
    plt.ylabel('CPU 사용률 (%)')
    plt.legend()
    plt.grid(True, alpha=0.3)
    
    # 3. 스레드 수 변화
    plt.subplot(3, 1, 3)
    plt.plot(df_sorted['datetime'], df_sorted['thread_count'], label='스레드 수', alpha=0.7, color='purple')
    plt.title('스레드 수 변화')
    plt.xlabel('시간')
    plt.ylabel('스레드 수')
    plt.legend()
    plt.grid(True, alpha=0.3)
    
    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, 'action_performance_timeline.png'), dpi=300, bbox_inches='tight')
    print("✅ action_performance_timeline.png 생성됨")
    
    # 액션별 성능 분포 차트
    if not custom_events.empty:
        plt.figure(figsize=(12, 8))
        
        # UI 상호작용 응답 시간 분포
        ui_events = custom_events[custom_events['heap_used_mb'] == 'UI_INTERACTION']
        if not ui_events.empty:
            response_times = []
            interaction_types = []
            
            for idx, event in ui_events.iterrows():
                try:
                    details = event['native_heap_mb']
                    detail_dict = dict(item.split(':') for item in details.split(',') if ':' in item)
                    response_time = int(detail_dict.get('response_time', '0').replace('ms', ''))
                    interaction_type = detail_dict.get('type', 'UNKNOWN')
                    
                    response_times.append(response_time)
                    interaction_types.append(interaction_type)
                except:
                    continue
            
            if response_times:
                plt.hist(response_times, bins=20, alpha=0.7, color='skyblue')
                plt.title('UI 상호작용 응답 시간 분포')
                plt.xlabel('응답 시간 (ms)')
                plt.ylabel('빈도')
                plt.grid(True, alpha=0.3)
                
                plt.savefig(os.path.join(output_dir, 'ui_response_time_distribution.png'), 
                           dpi=300, bbox_inches='tight')
                print("✅ ui_response_time_distribution.png 생성됨")

def main():
    parser = argparse.ArgumentParser(description='KISS Profile 로그 분석기')
    parser.add_argument('log_dir', nargs='?', default='.', 
                       help='로그 디렉토리 경로 (기본값: 현재 디렉토리)')
    parser.add_argument('--output', '-o', default='./profile_analysis', 
                       help='출력 디렉토리 (기본값: ./profile_analysis)')
    
    args = parser.parse_args()
    
    print("🚀 KISS Profile 로그 분석기 (고급 액션 분석 포함)")
    print("=" * 60)
    
    setup_plot_style()
    
    # 로그 로드
    result = load_performance_logs(args.log_dir)
    if result is None:
        sys.exit(1)
    
    df, custom_events = result
    
    print(f"\n📊 분석할 데이터: {len(df)}개 성능 레코드, {len(custom_events)}개 이벤트")
    
    # 기본 분석 수행
    analyze_memory_usage(df)
    analyze_cpu_performance(df)
    analyze_app_lifecycle(custom_events)
    analyze_search_performance(custom_events)
    
    # 고급 액션별 분석 수행
    analyze_user_actions(custom_events)
    analyze_performance_patterns(df, custom_events)
    
    # 시각화 생성
    create_visualizations(df, custom_events, args.output)
    create_action_performance_visualizations(df, custom_events, args.output)
    
    # HTML 보고서 생성
    generate_report(df, custom_events, args.output)
    
    print(f"\n✅ 고급 분석 완료! 결과는 {args.output} 디렉토리에 저장되었습니다.")
    print("📁 생성된 파일:")
    print("   - performance_overview.png")
    print("   - memory_analysis.png")
    print("   - action_performance_timeline.png") 
    print("   - ui_response_time_distribution.png")
    print("   - profile_report.html")

if __name__ == "__main__":
    main()
